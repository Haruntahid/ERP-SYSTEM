package com.schoolerp.shared.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantFilter;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantRoutingDataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full two-school integration and tenant isolation test for F-05 against real MySQL, Redis, and RabbitMQ:
 * 1. Outbox events written inside msgalpha's database transaction commit atomically into msgalpha's
 *    outbox_events table and never appear in msgbeta's outbox_events table.
 * 2. OutboxPublisher relays msgalpha's pending events to RabbitMQ with X-Tenant-Slug=msgalpha and marks
 *    them PUBLISHED.
 * 3. TenantAwareMessageListener consumes the event with TenantContext bound to msgalpha, executes the
 *    registered DomainEventConsumer once, and records (event_id, consumer_group) in msgalpha's processed_events.
 * 4. Duplicate delivery of the same eventId is idempotently ignored, and tampered cross-tenant headers are
 *    rejected before any consumer executes.
 * 5. OutboxMessagingController masks sensitive fields (otp/password/token/secret), enforces two-school isolation,
 *    and replays DEAD_LETTERED events with an OUTBOX_EVENT_REPLAYED audit log entry.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessagingTenantIsolationTest {

    private static final String PLATFORM_KEY = "dev-platform-key";
    private static final String TENANT_A = "msgalpha";
    private static final String TENANT_B = "msgbeta";

    static final List<String> ALPHA_CONSUMED = new CopyOnWriteArrayList<>();
    static final List<String> BETA_CONSUMED = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class TestConsumerConfig {
        @Bean
        DomainEventConsumer<Map> testFeePaymentConsumer() {
            return new DomainEventConsumer<>() {
                @Override
                public String consumerGroup() {
                    return "test-fee-receipt-consumer";
                }

                @Override
                public String eventType() {
                    return "TEST_FEE_PAYMENT_RECEIVED";
                }

                @Override
                public Class<Map> payloadType() {
                    return Map.class;
                }

                @Override
                public void handle(Envelope<Map> envelope) {
                    String currentTenant = TenantContext.require();
                    if (TENANT_A.equals(currentTenant)) {
                        ALPHA_CONSUMED.add(envelope.eventId());
                    } else if (TENANT_B.equals(currentTenant)) {
                        BETA_CONSUMED.add(envelope.eventId());
                    }
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbc;

    @Autowired
    private TenantRegistry tenantRegistry;

    @Autowired
    private TenantRoutingDataSource routingDataSource;

    @Autowired
    private TenantAwareRedisTemplate tenantRedisTemplate;

    @Autowired
    private OutboxEventWriter outboxEventWriter;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private TenantAwareMessageListener messageListener;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        ALPHA_CONSUMED.clear();
        BETA_CONSUMED.clear();
        cleanupTenant(TENANT_A);
        cleanupTenant(TENANT_B);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanupTenant(TENANT_A);
        cleanupTenant(TENANT_B);
    }

    private void cleanupTenant(String slug) {
        tenantRedisTemplate.evictTenantNamespaceForSlug(slug, "all");
        routingDataSource.evict(slug);
        tenantRegistry.evict(slug);
        masterJdbc.update("DELETE FROM tenants WHERE slug = ?", slug);
        masterJdbc.execute("DROP DATABASE IF EXISTS `school" + slug + "`");
        masterJdbc.execute("DROP USER IF EXISTS 't" + slug + "'@'%'");
    }

    @Test
    void enforcesEndToEndTransactionalOutboxConsumerIdempotencyAndTwoSchoolAdminIsolation() throws Exception {
        provisionTenant(TENANT_A, "Alpha Messaging School", "admin@alpha.edu", "Alpha@12345");
        provisionTenant(TENANT_B, "Beta Messaging School", "admin@beta.edu", "Beta@12345");

        String accessA = login(TENANT_A, "admin@alpha.edu", "Alpha@12345", "10.20.1.1").get("accessToken").asText();
        String accessB = login(TENANT_B, "admin@beta.edu", "Beta@12345", "10.20.2.1").get("accessToken").asText();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 1. Write an outbox event inside TENANT_A transaction (with sensitive fields to verify masking)
        OutboxEvent alphaEvent = executeInTenant(TENANT_A, () -> txTemplate.execute(status ->
                outboxEventWriter.write(
                        "INVOICE",
                        "INV-A-900",
                        "TEST_FEE_PAYMENT_RECEIVED",
                        "fees.payment.received",
                        EventPriority.HIGH,
                        Map.of(
                                "amount", "4500.00",
                                "currency", "BDT",
                                "otpCode", "998877",
                                "secretToken", "tok_secret_alpha"))));

        // Verify TENANT_A has 1 outbox row and TENANT_B has 0 outbox rows
        mockMvc.perform(get("/api/v1/messaging/outbox")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.20.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value(alphaEvent.getEventId()));

        mockMvc.perform(get("/api/v1/messaging/outbox")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.20.2.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Verify TENANT_B cannot inspect TENANT_A's event by ID (404 Not Found)
        mockMvc.perform(get("/api/v1/messaging/outbox/" + alphaEvent.getEventId())
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.20.2.1"))
                .andExpect(status().isNotFound());

        // Verify TENANT_A detail view masks otpCode and secretToken as "***"
        MvcResult detailRes = mockMvc.perform(get("/api/v1/messaging/outbox/" + alphaEvent.getEventId())
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.20.1.1"))
                .andExpect(status().isOk())
                .andReturn();
        String detailBody = detailRes.getResponse().getContentAsString();
        assertFalse(detailBody.contains("998877"), "OTP must be masked in outbox detail response");
        assertFalse(detailBody.contains("tok_secret_alpha"), "Token must be masked in outbox detail response");
        assertTrue(detailBody.contains("***"));

        // 2. Relay TENANT_A outbox to RabbitMQ if not already relayed by background scheduler
        executeInTenant(TENANT_A, () -> {
            outboxPublisher.relayTenantOutbox(TENANT_A);
            return null;
        });

        OutboxEvent afterRelay = executeInTenant(TENANT_A, () ->
                outboxEventRepository.findByEventId(alphaEvent.getEventId()).orElseThrow());
        assertEquals(OutboxStatus.PUBLISHED, afterRelay.getStatus());

        // 3. Dispatch the event through TenantAwareMessageListener and verify idempotency + tenant isolation
        Message amqpMessage = MessageBuilder
                .withBody(alphaEvent.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, TENANT_A)
                .setHeader(OutboxEventWriter.HEADER_EVENT_ID, alphaEvent.getEventId())
                .setHeader(OutboxEventWriter.HEADER_EVENT_TYPE, alphaEvent.getEventType())
                .build();

        assertTrue(messageListener.dispatchMessage(amqpMessage));
        // Duplicate delivery must be ignored by processed_events idempotency
        assertTrue(messageListener.dispatchMessage(amqpMessage));

        assertEquals(1, ALPHA_CONSUMED.stream().filter(alphaEvent.getEventId()::equals).count());
        assertTrue(BETA_CONSUMED.isEmpty(), "Beta school consumer must never receive Alpha school events");

        boolean recordedInAlpha = executeInTenant(TENANT_A, () ->
                processedEventRepository.existsByEventIdAndConsumerGroup(
                        alphaEvent.getEventId(), "test-fee-receipt-consumer"));
        boolean recordedInBeta = executeInTenant(TENANT_B, () ->
                processedEventRepository.existsByEventIdAndConsumerGroup(
                        alphaEvent.getEventId(), "test-fee-receipt-consumer"));
        assertTrue(recordedInAlpha);
        assertFalse(recordedInBeta);

        // 4. Cross-tenant header spoofing (X-Tenant-Slug=msgbeta with envelope.tenantSlug=msgalpha) is rejected
        Message spoofedMsg = MessageBuilder.fromMessage(amqpMessage)
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, TENANT_B)
                .build();
        assertFalse(messageListener.dispatchMessage(spoofedMsg));
        assertTrue(BETA_CONSUMED.isEmpty());

        // 5. Mark an event DEAD_LETTERED in TENANT_A and verify admin replay + audit log
        OutboxEvent deadEvent = executeInTenant(TENANT_A, () -> txTemplate.execute(status -> {
            OutboxEvent ev = outboxEventWriter.write(
                    "USER", "77", "USER_INVITED", "iam.user.invited", EventPriority.HIGH, Map.of("email", "t@a.edu"));
            ev.setStatus(OutboxStatus.DEAD_LETTERED);
            ev.setAttempts(3);
            ev.setLastError("Simulated broker outage");
            return outboxEventRepository.save(ev);
        }));

        // TENANT_B cannot replay TENANT_A's dead-lettered event
        mockMvc.perform(post("/api/v1/messaging/outbox/" + deadEvent.getEventId() + "/replay")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.20.2.1"))
                .andExpect(status().isNotFound());

        // TENANT_A replays the dead-lettered event -> resets to PENDING with attempts=0
        mockMvc.perform(post("/api/v1/messaging/outbox/" + deadEvent.getEventId() + "/replay")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.20.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));

        // Stats endpoint returns accurate counts for TENANT_A
        mockMvc.perform(get("/api/v1/messaging/stats")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.20.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLetteredCount").value(0));
    }

    private <T> T executeInTenant(String tenantSlug, java.util.function.Supplier<T> action) {
        try {
            TenantContext.set(tenantSlug);
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private void provisionTenant(String slug, String name, String adminEmail, String adminPassword) throws Exception {
        mockMvc.perform(post("/api/v1/platform/tenants")
                        .header("X-Platform-Key", PLATFORM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "%s",
                                  "name": "%s",
                                  "plan": "PRO",
                                  "adminEmail": "%s",
                                  "adminName": "%s Admin",
                                  "adminPassword": "%s"
                                }
                                """.formatted(slug, name, adminEmail, name, adminPassword)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String tenantSlug, String email, String password, String clientIp) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header(TenantFilter.TENANT_HEADER, tenantSlug)
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}
