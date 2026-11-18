package edu.eci.dosw.campusseguro.training.tutor;

import java.util.List;
import java.util.UUID;

// Answer of the tutor. sufficient=false means it had no information and the answer points to the official
// protocol; then there are no sources. Otherwise sources are the contents the answer is based on (RF-15 rule 4).
public record TutorAnswerResponse(String answer, boolean sufficient, List<Source> sources) {

    public record Source(UUID id, String title) {
    }
}
