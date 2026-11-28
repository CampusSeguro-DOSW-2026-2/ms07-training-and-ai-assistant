package edu.eci.dosw.campusseguro.training.content;

import java.util.UUID;

// A content was created, edited or reactivated. The tutor recalculates its embedding after the commit.
public record ContentChangedEvent(UUID contentId) {
}
