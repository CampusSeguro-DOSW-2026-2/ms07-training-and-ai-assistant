package edu.eci.dosw.campusseguro.training.evaluation.draft;

import edu.eci.dosw.campusseguro.training.evaluation.EvaluationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// In-memory drafts. A draft is never saved in the database: the ADMIN reviews it and saves it with
// POST /api/v1/training/evaluations. After TTL it is gone (404), and so it is after a restart of MS07.
@Component
public class EvaluationDraftStore {

    public static final Duration TTL = Duration.ofMinutes(30);

    // Snapshot of a draft. Each change replaces it in the map.
    public record Draft(UUID id, UUID ownerId, Instant createdAt, DraftStatus status, EvaluationRequest result,
                        String message) {

        boolean expired(Instant now) {
            return !now.isBefore(createdAt.plus(TTL));
        }
    }

    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public EvaluationDraftStore() {
        this(Clock.systemUTC());
    }

    EvaluationDraftStore(Clock clock) {
        this.clock = clock;
    }

    public Draft create(UUID ownerId) {
        Instant now = clock.instant();
        drafts.values().removeIf(draft -> draft.expired(now));
        Draft draft = new Draft(UUID.randomUUID(), ownerId, now, DraftStatus.EN_PROCESO, null, null);
        drafts.put(draft.id(), draft);
        return draft;
    }

    // Only the user who asked for the draft sees it, and only before it expires.
    public Optional<Draft> find(UUID id, UUID ownerId) {
        Draft draft = drafts.get(id);
        if (draft == null) {
            return Optional.empty();
        }
        if (draft.expired(clock.instant())) {
            drafts.remove(id);
            return Optional.empty();
        }
        return draft.ownerId().equals(ownerId) ? Optional.of(draft) : Optional.empty();
    }

    public void ready(UUID id, EvaluationRequest result) {
        drafts.computeIfPresent(id, (key, draft) ->
                new Draft(draft.id(), draft.ownerId(), draft.createdAt(), DraftStatus.LISTO, result, null));
    }

    public void failed(UUID id, String message) {
        drafts.computeIfPresent(id, (key, draft) ->
                new Draft(draft.id(), draft.ownerId(), draft.createdAt(), DraftStatus.FALLIDO, null, message));
    }

    public void remove(UUID id) {
        drafts.remove(id);
    }

    int size() {
        return drafts.size();
    }
}
