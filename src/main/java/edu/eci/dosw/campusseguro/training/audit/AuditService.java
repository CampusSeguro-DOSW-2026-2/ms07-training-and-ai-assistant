package edu.eci.dosw.campusseguro.training.audit;

import edu.eci.dosw.campusseguro.training.audit.gateway.AuditGatewayPayload;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AuditService(AuditEventRepository auditEventRepository) {
        this(auditEventRepository, event -> {});
    }

    @Autowired
    public AuditService(
            AuditEventRepository auditEventRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.auditEventRepository = auditEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void registerTutorQuery(UUID userId, TutorQueryResult result) {
        if (result == null) {
            throw new IllegalArgumentException("The result is required for a tutor query");
        }
        save(userId, AuditEvent.TUTOR_QUERY, result.name());
    }

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

        String gatewayCorrelationId = correlationId != null
                ? correlationId
                : UUID.randomUUID().toString();

        AuditGatewayPayload payload = new AuditGatewayPayload(
                event.getId(),
                userId,
                "MS07",
                eventType,
                null,
                "EXITO",
                event.getOccurredAt(),
                gatewayCorrelationId,
                result,
                null,
                null,
                null
        );

        eventPublisher.publishEvent(payload);
    }
}
