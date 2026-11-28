package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.Moderation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

// RF-15: moderation of the redacted question and of the answer of the model, with the moderation of the provider.
// If the moderation fails or is not available the tutor follows its normal flow: it never blocks the service.
@Component
public class TutorModeration {

    public enum Verdict { ALLOWED, CRISIS, NOT_ALLOWED }

    // Self-harm: the person may be at risk, so the tutor answers with care instead of blocking.
    static final Set<String> CRISIS_CATEGORIES = Set.of("self-harm", "self-harm/intent", "self-harm/instructions");

    // Categories the moderation gives to real emergency questions, so alone they never block: "un compañero está
    // sangrando mucho" and "hay un incendio y gente herida" come as violence, and even "cómo evacuar el edificio" comes
    // as illicit/violent. A question out of scope finds no contents and never reaches the model anyway.
    static final Set<String> EMERGENCY_LIKE = Set.of("violence", "violence/graphic", "illicit", "illicit/violent");

    // Safety net that does not depend on the provider: if the moderation fails or is slow, a question with one of
    // these phrases still gets the message of care. Compared without accents or case.
    static final List<String> CRISIS_PHRASES = List.of("quiero morir", "me quiero morir", "no quiero vivir",
            "quitarme la vida", "acabar con mi vida", "suicidarme", "suicidio", "me voy a suicidar", "matarme",
            "hacerme dano", "me quiero hacer dano", "me voy a hacer dano", "lastimarme", "cortarme las venas", "no vale la pena vivir");

    private static final Logger log = LoggerFactory.getLogger(TutorModeration.class);

    private final AiAdapters adapters;

    public TutorModeration(AiAdapters adapters) {
        this.adapters = adapters;
    }

    public Verdict check(String text) {
        String normalized = " " + InputAnalyzer.normalize(text) + " ";
        if (CRISIS_PHRASES.stream().anyMatch(phrase -> normalized.contains(" " + phrase + " ")
                || normalized.contains(" " + phrase))) {
            return Verdict.CRISIS;
        }
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
        // Hate, harassment, threats against people or sexual content are blocked ("quiero matar a mi profesor" comes
        // as harassment/threatening). Violence or illicit alone are not: see EMERGENCY_LIKE.
        if (EMERGENCY_LIKE.containsAll(moderation.categories())) {
            return Verdict.ALLOWED;
        }
        return Verdict.NOT_ALLOWED;
    }
}
