package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.Moderation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

// RF-15: moderation of the redacted question and of the answer of the model, with the moderation of the provider.
// If the moderation fails or is not available the tutor follows its normal flow: it never blocks the service.
@Component
public class TutorModeration {

    public enum Verdict { ALLOWED, CRISIS, NOT_ALLOWED }

    // Self-harm: the person may be at risk, so the tutor answers with care instead of blocking.
    static final Set<String> CRISIS_CATEGORIES = Set.of("self-harm", "self-harm/intent", "self-harm/instructions");

    static final Set<String> VIOLENCE_ONLY = Set.of("violence", "violence/graphic");

    private static final Logger log = LoggerFactory.getLogger(TutorModeration.class);

    private final AiAdapters adapters;

    public TutorModeration(AiAdapters adapters) {
        this.adapters = adapters;
    }

    public Verdict check(String text) {
        Moderation moderation;
        try {
            moderation = adapters.current().moderate(text);
        } catch (RuntimeException e) {
            log.warn("Moderation not available: {}", e.getClass().getSimpleName());
            return Verdict.ALLOWED;
        }
        if (!moderation.available() || !moderation.flagged()) {
            return Verdict.ALLOWED;
        }
        if (moderation.categories().stream().anyMatch(CRISIS_CATEGORIES::contains)) {
            return Verdict.CRISIS;
        }
        // "violence" alone is not blocked: the moderation marks real emergencies that way ("un compañero está
        // sangrando mucho", "hay un incendio y gente herida"). It is blocked with threats, harassment, hate, sexual or
        // illicit content ("quiero matar a...", "cómo hago una bomba"), or any other flag.
        if (VIOLENCE_ONLY.containsAll(moderation.categories())) {
            return Verdict.ALLOWED;
        }
        return Verdict.NOT_ALLOWED;
    }
}
