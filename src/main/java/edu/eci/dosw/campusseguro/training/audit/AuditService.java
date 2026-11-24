package edu.eci.dosw.campusseguro.training.audit;

import edu.eci.dosw.campusseguro.training.audit.gateway.AuditGatewayPayload;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerTutorQuery(UUID userId, TutorQueryResult result) {
        if (result == null) {
            throw new IllegalArgumentException("The result is required for a tutor query");
        }
        save(userId, AuditEvent.TUTOR_QUERY, result.name(), null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(UUID userId, String eventType) {
        register(userId, eventType, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(
            UUID userId,
            String eventType,
            String affectedResource,
            Map<String, Object> attributes
    ) {
        save(userId, eventType, null, affectedResource, attributes);
    }

    private void save(
            UUID userId,
            String eventType,
            String result,
            String affectedResource,
            Map<String, Object> attributes
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("The user ID is required for audit registration");
        }

        HttpServletRequest request = currentRequest();
        // Use the normalized correlation ID established by the request filter.
        String correlationId = MDC.get("correlationId");

        AuditEvent event = new AuditEvent(userId, eventType, correlationId, result);
        auditEventRepository.save(event);

        String gatewayCorrelationId = correlationId != null && !correlationId.isBlank()
                ? correlationId
                : UUID.randomUUID().toString();

        String role = request != null ? request.getHeader("X-User-Role") : null;
        String permission = request != null ? request.getHeader("X-User-Permission") : null;

        AuditGatewayPayload payload = new AuditGatewayPayload(
                event.getId(),
                userId,
                "MS07",
                eventType,
                affectedResource,
                "EXITO",
                event.getOccurredAt(),
                gatewayCorrelationId,
                result,
                null,
                null,
                attributes,
                role,
                permission
        );

        eventPublisher.publishEvent(payload);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
