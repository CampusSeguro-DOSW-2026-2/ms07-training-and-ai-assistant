package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiPrompt;
import edu.eci.dosw.campusseguro.training.tutor.ai.Moderation;
import edu.eci.dosw.campusseguro.training.tutor.ai.TutorProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TutorModerationTest {

    private static TutorModeration with(Function<String, Moderation> result) {
        AiAdapter adapter = new AiAdapter() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public String complete(AiPrompt prompt) {
                return "";
            }

            @Override
            public Moderation moderate(String text) {
                return result.apply(text);
            }
        };
        return new TutorModeration(new AiAdapters(List.of(adapter), new TutorProperties("fake", null, null, null, null,
                null, null)));
    }

    private static TutorModeration.Verdict verdict(Set<String> categories) {
        return with(text -> Moderation.flagged(categories)).check("texto");
    }

    @Test
    void selfHarmIsACrisis() {
        assertThat(verdict(Set.of("self-harm"))).isEqualTo(TutorModeration.Verdict.CRISIS);
        assertThat(verdict(Set.of("self-harm/intent", "violence"))).isEqualTo(TutorModeration.Verdict.CRISIS);
    }

    @Test
    void hateHarassmentAndSexualContentAreNotAllowed() {
        for (String category : List.of("hate", "harassment/threatening", "sexual", "sexual/minors")) {
            assertThat(verdict(Set.of(category))).as(category).isEqualTo(TutorModeration.Verdict.NOT_ALLOWED);
        }
        assertThat(verdict(Set.of("violence", "harassment/threatening"))).isEqualTo(TutorModeration.Verdict.NOT_ALLOWED);
    }

    // The real moderation marks emergencies as violence ("un compañero está sangrando mucho") and even "cómo evacuar
    // el edificio" as illicit/violent: they are answered
    @Test
    void violenceOrIllicitAloneIsAllowed() {
        assertThat(verdict(Set.of("violence"))).isEqualTo(TutorModeration.Verdict.ALLOWED);
        assertThat(verdict(Set.of("violence", "violence/graphic"))).isEqualTo(TutorModeration.Verdict.ALLOWED);
        assertThat(verdict(Set.of("illicit/violent"))).isEqualTo(TutorModeration.Verdict.ALLOWED);
        assertThat(verdict(Set.of("illicit", "illicit/violent"))).isEqualTo(TutorModeration.Verdict.ALLOWED);
    }

    @Test
    void selfHarmPhrasesAreACrisisEvenIfTheModerationFails() {
        TutorModeration failing = with(text -> {
            throw new IllegalStateException("timeout");
        });

        for (String question : List.of("me quiero hacer daño", "Ya no quiero vivir", "pienso en quitarme la vida",
                "quiero suicidarme")) {
            assertThat(failing.check(question)).as(question).isEqualTo(TutorModeration.Verdict.CRISIS);
        }
        assertThat(failing.check("¿qué hago si un compañero se hace daño en el laboratorio?"))
                .isEqualTo(TutorModeration.Verdict.ALLOWED);
    }

    @Test
    void cleanUnavailableOrFailingModerationIsAllowed() {
        assertThat(with(text -> Moderation.clean()).check("texto")).isEqualTo(TutorModeration.Verdict.ALLOWED);
        assertThat(with(text -> Moderation.unavailable()).check("texto")).isEqualTo(TutorModeration.Verdict.ALLOWED);
        assertThat(with(text -> {
            throw new IllegalStateException("down");
        }).check("texto")).isEqualTo(TutorModeration.Verdict.ALLOWED);
    }
}
