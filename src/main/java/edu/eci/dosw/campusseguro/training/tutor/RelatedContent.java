package edu.eci.dosw.campusseguro.training.tutor;

import java.util.UUID;

// A content related to the question. The text is cut so the prompt stays small; the title is the source shown to the user.
public record RelatedContent(UUID id, String title, String text, int score) {
}
