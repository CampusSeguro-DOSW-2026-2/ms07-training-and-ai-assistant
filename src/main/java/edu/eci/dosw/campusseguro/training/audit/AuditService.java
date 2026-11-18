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
        if (userId == null) {
            throw new IllegalArgumentException("The user ID is required for audit registration");
        }
        if (result == null) {
            throw new IllegalArgumentException("The result is required for a tutor query");
        }

        String correlationId = MDC.get("correlationId");

        AuditEvent event = new AuditEvent(
                userId,
                AuditEvent.TUTOR_QUERY,
                correlationId,
                result.name()
        );

        auditEventRepository.save(event);
    }
}
