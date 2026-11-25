package edu.eci.dosw.campusseguro.training.audit.gateway;

import org.junit.jupiter.api.Assertions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AuditGatewayClientTest {

    private static final String GATEWAY_URL =
            "http://localhost:8080/api/v1/audit/events";

    private static final String SERVICE_KEY = "test-service-key";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private AuditGatewayClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuditGatewayClient(builder, GATEWAY_URL, SERVICE_KEY);
    }

    @Test
    void sendsAuditPayloadWithExpectedHeadersAndJson() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String role = "BRIGADISTA";
        String permission = "ms07:content:manage";

        AuditGatewayPayload payload = new AuditGatewayPayload(
                UUID.randomUUID(),
                userId,
                "MS07",
                "CONTENIDO_CREADO",
                "contenido:" + contentId,
                "EXITO",
                Instant.parse("2026-11-18T14:32:10Z"),
                correlationId.toString(),
                null,
                eventId,
                null,
                Map.of("categoria_id", categoryId.toString()),
                role,
                permission
        );

        server.expect(requestTo(GATEWAY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Key", SERVICE_KEY))
                .andExpect(header("X-Correlation-ID", correlationId.toString()))
                .andExpect(header("X-User-Id", userId.toString()))
                .andExpect(header("X-User-Role", role))
                .andExpect(header("X-User-Permission", permission))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "id": "%s",
                          "usuario_id": "%s",
                          "modulo": "MS07",
                          "accion": "CONTENIDO_CREADO",
                          "recurso_afectado": "contenido:%s",
                          "resultado": "EXITO",
                          "fecha_hora": "2026-11-18T14:32:10Z",
                          "correlation_id": "%s",
                          "evento_id": "%s",
                          "tipo_evento": null,
                          "atributos": {
                            "categoria_id": "%s"
                          }
                        }
                        """.formatted(
                        payload.id(),
                        userId,
                        contentId,
                        correlationId,
                        eventId,
                        categoryId
                )))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.send(payload);

        server.verify();
    }

    @Test
    void doesNotSendUserHeaderWhenEventIsAnonymous() {
        AuditGatewayPayload payload = new AuditGatewayPayload(
                UUID.randomUUID(),
                null,
                "MS07",
                "CONTENT_VIEWED",
                "CONTENT:456",
                "EXITO",
                Instant.parse("2026-11-18T14:32:10Z"),
                "anonymous-correlation",
                null,
                null,
                null,
                null
        );

        server.expect(requestTo(GATEWAY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Key", SERVICE_KEY))
                .andExpect(header("X-Correlation-ID", "anonymous-correlation"))
                .andExpect(request -> {
                    org.junit.jupiter.api.Assertions.assertFalse(
                            request.getHeaders().containsHeader("X-User-Id")
                    );
                })
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.send(payload);

        server.verify();
    }

    @Test
    void gatewayFailureDoesNotPropagateToCaller() {
        AuditGatewayPayload payload = new AuditGatewayPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MS07",
                "CONTENT_CREATED",
                "CONTENT:789",
                "EXITO",
                Instant.parse("2026-11-18T14:32:10Z"),
                "failure-correlation",
                null,
                null,
                null,
                null
        );

        server.expect(requestTo(GATEWAY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertDoesNotThrow(() -> client.send(payload));

        server.verify();
    }

    @Test
    void doesNotSendWhenGatewayConfigurationIsMissing() {
        AuditGatewayClient unconfiguredClient =
                new AuditGatewayClient(builder, "", "");

        AuditGatewayPayload payload = new AuditGatewayPayload(
                UUID.randomUUID(),
                null,
                "MS07",
                "CONTENT_VIEWED",
                null,
                "EXITO",
                Instant.parse("2026-11-18T14:32:10Z"),
                "unconfigured-correlation",
                null,
                null,
                null,
                null
        );

        assertDoesNotThrow(() -> unconfiguredClient.send(payload));

        server.verify();
    }

    @Test
    void gatewayListenerRunsOnlyAfterTransactionCommit() throws Exception {
        var method = AuditGatewayClient.class.getMethod(
                "send",
                AuditGatewayPayload.class
        );

        var listener = method.getAnnotation(TransactionalEventListener.class);

        Assertions.assertNotNull(listener);
        Assertions.assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
    }

    @Test
    void gatewayListenerUsesDedicatedAsyncExecutor() throws Exception {
        var method = AuditGatewayClient.class.getMethod(
                "send",
                AuditGatewayPayload.class
        );

        var async = method.getAnnotation(Async.class);

        Assertions.assertNotNull(async);
        Assertions.assertEquals("auditTaskExecutor", async.value());
    }

}
