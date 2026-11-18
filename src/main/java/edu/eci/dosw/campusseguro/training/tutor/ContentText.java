package edu.eci.dosw.campusseguro.training.tutor;

import java.util.UUID;

// Only what the tutor reads from a content: it does not load the category or the dates.
public interface ContentText {

    UUID getId();

    String getTitle();

    String getBody();
}
