package edu.eci.dosw.campusseguro.training.evaluation.draft;

import edu.eci.dosw.campusseguro.training.evaluation.EvaluationRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationDraftStoreTest {

    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-28T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final UUID ADMIN = UUID.randomUUID();

    private final MovableClock clock = new MovableClock();
    private final EvaluationDraftStore store = new EvaluationDraftStore(clock);

    @Test
    void aNewDraftIsInProgressAndOnlyItsOwnerSeesIt() {
        EvaluationDraftStore.Draft draft = store.create(ADMIN);

        assertThat(store.find(draft.id(), ADMIN)).get()
                .extracting(EvaluationDraftStore.Draft::status).isEqualTo(DraftStatus.EN_PROCESO);
        assertThat(store.find(draft.id(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void keepsTheResultOrTheFailure() {
        EvaluationDraftStore.Draft ok = store.create(ADMIN);
        EvaluationDraftStore.Draft ko = store.create(ADMIN);
        EvaluationRequest result = new EvaluationRequest("T", "t", UUID.randomUUID(), List.of());

        store.ready(ok.id(), result);
        store.failed(ko.id(), "No se pudo.");

        assertThat(store.find(ok.id(), ADMIN).orElseThrow().result()).isEqualTo(result);
        assertThat(store.find(ko.id(), ADMIN).orElseThrow())
                .extracting(EvaluationDraftStore.Draft::status, EvaluationDraftStore.Draft::message)
                .containsExactly(DraftStatus.FALLIDO, "No se pudo.");
    }

    @Test
    void aDraftExpiresAfterThirtyMinutes() {
        EvaluationDraftStore.Draft draft = store.create(ADMIN);

        clock.now = clock.now.plus(Duration.ofMinutes(29));
        assertThat(store.find(draft.id(), ADMIN)).isPresent();

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThat(store.find(draft.id(), ADMIN)).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void expiredDraftsAreRemovedWhenANewOneIsCreated() {
        store.create(ADMIN);
        store.create(ADMIN);
        clock.now = clock.now.plus(EvaluationDraftStore.TTL);

        store.create(ADMIN);

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void anUnknownDraftIsNotFound() {
        assertThat(store.find(UUID.randomUUID(), ADMIN)).isEmpty();
    }
}
