package edu.eci.dosw.campusseguro.training.audit.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

@Component
public class AuditGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(AuditGatewayClient.class);

    private final RestClient restClient;
    private final String gatewayUrl;
    private final String serviceKey;

    public AuditGatewayClient(
            RestClient.Builder builder,
            @Value("${campusseguro.audit.gateway-url:}") String gatewayUrl,
            @Value("${campusseguro.audit.service-key:}") String serviceKey
    ) {
        this.restClient = builder.build();
        this.gatewayUrl = gatewayUrl;
        this.serviceKey = serviceKey;
    }

    @Async("auditTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(AuditGatewayPayload payload) {
        if (gatewayUrl == null || gatewayUrl.isBlank()
                || serviceKey == null || serviceKey.isBlank()) {
            log.warn("Audit Gateway integration is not configured; event {} was not sent",
                    payload.id());
            return;
        }

        try {
            restClient.post()
                    .uri(gatewayUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Service-Key", serviceKey)
                    .header("X-Service-Name", "MS07")
                    .header("X-Correlation-ID", payload.correlationId())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .headers(headers -> {
                        if (payload.userId() != null) {
                            headers.set("X-User-Id", payload.userId().toString());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Audit event {} accepted by Gateway", payload.id());

        } catch (Exception exception) {
            log.error("Could not send audit event {} to Gateway", payload.id(), exception);
        }
    }
}
