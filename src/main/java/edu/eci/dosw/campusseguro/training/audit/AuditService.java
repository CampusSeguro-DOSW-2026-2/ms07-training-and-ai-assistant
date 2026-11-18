package edu.eci.dosw.campusseguro.training.audit;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void registerTutorQuery(UUID userId, TutorQueryResult result) {
        if (result == null) {
            throw new IllegalArgumentException("The result is required for a tutor query");
        }
        save(userId, AuditEvent.TUTOR_QUERY, result.name());
    }

    // Saves an event of the given type (one of the AuditEvent constants) in the current transaction.
    @Transactional
    public void register(UUID userId, String eventType) {
        save(userId, eventType, null);
    }

    private void save(UUID userId, String eventType, String result) {
        if (userId == null) {
            throw new IllegalArgumentException("The user ID is required for audit registration");
        }

        String correlationId = MDC.get("correlationId");

        AuditEvent event = new AuditEvent(
                userId,
                eventType,
                correlationId,
                result
        );

        auditEventRepository.save(event);
    }
}
