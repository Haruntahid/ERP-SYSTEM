package com.schoolerp.shared.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.tenancy.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OutboxEventWriterTest {

    private OutboxEventRepository repository;
    private SettingsService settingsService;
    private ObjectMapper objectMapper;
    private OutboxEventWriter writer;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        settingsService = mock(SettingsService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        writer = new OutboxEventWriter(repository, settingsService, objectMapper);
        when(settingsService.getInt("outbox.max_attempts", 3)).thenReturn(3);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void serializesEnvelopeWithTenantSlugFromContextAndPersistsRow() throws Exception {
        TenantContext.set("school-a");
        TransactionSynchronizationManager.setActualTransactionActive(true);

        OutboxEvent saved = writer.write(
                "INVOICE",
                "INV-1001",
                "FEE_PAYMENT_RECEIVED",
                "fees.payment.received",
                EventPriority.HIGH,
                Map.of("amount", "2500.00", "currency", "BDT"));

        assertNotNull(saved.getEventId());
        assertEquals("INVOICE", saved.getAggregateType());
        assertEquals("INV-1001", saved.getAggregateId());
        assertEquals("FEE_PAYMENT_RECEIVED", saved.getEventType());
        assertEquals("fees.payment.received", saved.getRoutingKey());
        assertEquals(EventPriority.HIGH, saved.getPriority());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttempts());
        assertEquals(3, saved.getMaxAttempts());

        JsonNode envelopeNode = objectMapper.readTree(saved.getPayloadJson());
        assertEquals("school-a", envelopeNode.get("tenantSlug").asText());
        assertEquals("FEE_PAYMENT_RECEIVED", envelopeNode.get("eventType").asText());
        assertEquals(saved.getEventId(), envelopeNode.get("eventId").asText());
        assertEquals("2500.00", envelopeNode.get("payload").get("amount").asText());

        JsonNode headersNode = objectMapper.readTree(saved.getHeadersJson());
        assertEquals("school-a", headersNode.get(OutboxEventWriter.HEADER_TENANT_SLUG).asText());
        assertEquals(saved.getEventId(), headersNode.get(OutboxEventWriter.HEADER_EVENT_ID).asText());
    }

    @Test
    void throwsWhenNoActiveTransactionBoundary() {
        TenantContext.set("school-a");
        TransactionSynchronizationManager.setActualTransactionActive(false);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> writer.write("USER", "42", "USER_INVITED", "iam.user.invited", EventPriority.HIGH, Map.of()));
        assertTrue(ex.getMessage().contains("@Transactional"));
    }

    @Test
    void throwsWhenNoTenantBound() {
        TenantContext.clear();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThrows(
                IllegalStateException.class,
                () -> writer.write("USER", "42", "USER_INVITED", "iam.user.invited", EventPriority.HIGH, Map.of()));
    }
}

