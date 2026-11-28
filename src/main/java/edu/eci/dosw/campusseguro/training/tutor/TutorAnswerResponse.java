package edu.eci.dosw.campusseguro.training.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

// Answer of the tutor. sufficient=false means it had no information and the answer points to the official
// protocol; then there are no sources. Otherwise sources are the contents the answer is based on (RF-15 rule 4).
// notice is only present when the question was not answered on purpose: CRISIS (a message of care with the
// emergency line 123) or NO_PERMITIDO (a neutral reminder of what the tutor is for). The front shows them apart.
public record TutorAnswerResponse(String answer, boolean sufficient, List<Source> sources,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) String notice) {

    public static final String CRISIS = "CRISIS";
    public static final String NOT_ALLOWED = "NO_PERMITIDO";

    public TutorAnswerResponse(String answer, boolean sufficient, List<Source> sources) {
        this(answer, sufficient, sources, null);
    }

    public record Source(UUID id, String title) {
    }
}
