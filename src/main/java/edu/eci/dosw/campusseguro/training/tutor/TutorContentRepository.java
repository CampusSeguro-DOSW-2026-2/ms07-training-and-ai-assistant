package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.content.Content;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// Read-only access of the tutor to the contents (RF-10 rule 3: an inactive content is not used by the tutor).
// The status of the category does not matter: an active content of an inactive category stays visible (RF-12).
public interface TutorContentRepository extends Repository<Content, UUID> {

    List<ContentText> findByStatus(Status status);
}
