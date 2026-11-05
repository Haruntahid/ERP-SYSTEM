package com.schoolerp.shared.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class TenantAwareMessageListenerTest {

    private TenantRegistry tenantRegistry;
    private ProcessedEventRepository processedEventRepository;
    private OutboxEventRepository outboxEventRepository;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        tenantRegistry = mock(TenantRegistry.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());

        TenantInfo activeSchoolA = new TenantInfo(
                1L, "school-a", "School A", TenantStatus.ACTIVE, "PRO",
                "jdbc:mysql://localhost:3307/school_a", "u", "p", Map.of());
        when(tenantRegistry.find("school-a")).thenReturn(Optional.of(activeSchoolA));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bindsAndClearsTenantContextAroundConsumerAndRecordsProcessedEvent() throws Exception {
        AtomicReference<String> observedTenant = new AtomicReference<>();
        AtomicReference<String> observedPayloadValue = new AtomicReference<>();

        DomainEventConsumer<Map> consumer = new DomainEventConsumer<>() {
            @Override
            public String consumerGroup() {
                return "notification-dispatcher";
            }

            @Override
            public String eventType() {
                return "FEE_PAYMENT_RECEIVED";
            }

            @Override
            public Class<Map> payloadType() {
                return Map.class;
            }

            @Override
            public void handle(Envelope<Map> envelope) {
                observedTenant.set(TenantContext.require());
                observedPayloadValue.set(String.valueOf(envelope.payload().get("receiptNo")));
            }
        };

        TenantAwareMessageListener listener = new TenantAwareMessageListener(
                tenantRegistry,
                processedEventRepository,
                outboxEventRepository,
                rabbitTemplate,
                objectMapper,
                transactionManager,
                List.of(consumer));

        Envelope<Map<String, String>> env = new Envelope<>(
                "evt-001", "FEE_PAYMENT_RECEIVED", "school-a", "corr-001",
                Instant.now(), Map.of("receiptNo", "RCP-2026-01"));
        Message msg = MessageBuilder
                .withBody(objectMapper.writeValueAsString(env).getBytes(StandardCharsets.UTF_8))
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, "school-a")
                .setHeader(OutboxEventWriter.HEADER_EVENT_ID, "evt-001")
                .setHeader(OutboxEventWriter.HEADER_EVENT_TYPE, "FEE_PAYMENT_RECEIVED")
                .build();

        boolean handled = listener.dispatchMessage(msg);

        assertTrue(handled);
        assertEquals("school-a", observedTenant.get());
        assertEquals("RCP-2026-01", observedPayloadValue.get());
        assertTrue(TenantContext.get().isEmpty(), "TenantContext must be cleared after listener finishes");
        verify(processedEventRepository, times(1)).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    void skipsConsumerOnDuplicateEventIdForSameConsumerGroup() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        when(processedEventRepository.existsByEventIdAndConsumerGroup("evt-dup-1", "notification-dispatcher"))
                .thenReturn(true);

        DomainEventConsumer<Map> consumer = new DomainEventConsumer<>() {
            @Override
            public String consumerGroup() {
                return "notification-dispatcher";
            }

            @Override
            public String eventType() {
                return "FEE_PAYMENT_RECEIVED";
            }

            @Override
            public Class<Map> payloadType() {
                return Map.class;
            }

            @Override
            public void handle(Envelope<Map> envelope) {
                executionCount.incrementAndGet();
            }
        };

        TenantAwareMessageListener listener = new TenantAwareMessageListener(
                tenantRegistry,
                processedEventRepository,
                outboxEventRepository,
                rabbitTemplate,
                objectMapper,
                transactionManager,
                List.of(consumer));

        Envelope<Map<String, String>> env = new Envelope<>(
                "evt-dup-1", "FEE_PAYMENT_RECEIVED", "school-a", "corr-dup",
                Instant.now(), Map.of("receiptNo", "RCP-DUP"));
        Message msg = MessageBuilder
                .withBody(objectMapper.writeValueAsString(env).getBytes(StandardCharsets.UTF_8))
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, "school-a")
                .build();

        assertTrue(listener.dispatchMessage(msg));
        assertEquals(0, executionCount.get(), "Duplicate event must not re-run consumer logic");
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMessageAndRoutesToDlqWhenTenantHeaderMissingOrMismatched() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        DomainEventConsumer<Map> consumer = new DomainEventConsumer<>() {
            @Override
            public String consumerGroup() {
                return "notification-dispatcher";
            }

            @Override
            public String eventType() {
                return "FEE_PAYMENT_RECEIVED";
            }

            @Override
            public Class<Map> payloadType() {
                return Map.class;
            }

            @Override
            public void handle(Envelope<Map> envelope) {
                executionCount.incrementAndGet();
            }
        };

        TenantAwareMessageListener listener = new TenantAwareMessageListener(
                tenantRegistry,
                processedEventRepository,
                outboxEventRepository,
                rabbitTemplate,
                objectMapper,
                transactionManager,
                List.of(consumer));

        // Header claims school-a, envelope claims school-b -> must reject!
        Envelope<Map<String, String>> mismatchedEnvelope = new Envelope<>(
                "evt-spoof", "FEE_PAYMENT_RECEIVED", "school-b", "corr-spoof",
                Instant.now(), Map.of("receiptNo", "RCP-SPOOF"));
        Message mismatchedMsg = MessageBuilder
                .withBody(objectMapper.writeValueAsString(mismatchedEnvelope).getBytes(StandardCharsets.UTF_8))
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, "school-a")
                .build();

        assertFalse(listener.dispatchMessage(mismatchedMsg));
        assertEquals(0, executionCount.get());
        assertTrue(TenantContext.get().isEmpty());
        verify(rabbitTemplate, times(1)).send(
                eq(RabbitMqTopologyConfig.EXCHANGE_DLX),
                eq(RabbitMqTopologyConfig.ROUTING_DLQ),
                any(Message.class));
    }

    @Test
    void clearsTenantContextAndTransitionsOutboxToDeadLetteredAfterMaxAttempts() throws Exception {
        OutboxEvent outboxRow = new OutboxEvent();
        outboxRow.setEventId("evt-fail-1");
        outboxRow.setAttempts(2);
        outboxRow.setMaxAttempts(3);
        outboxRow.setStatus(OutboxStatus.FAILED);
        when(outboxEventRepository.findByEventId("evt-fail-1")).thenReturn(Optional.of(outboxRow));

        DomainEventConsumer<Map> failingConsumer = new DomainEventConsumer<>() {
            @Override
            public String consumerGroup() {
                return "sms-gateway";
            }

            @Override
            public String eventType() {
                return "USER_OTP_ISSUED";
            }

            @Override
            public Class<Map> payloadType() {
                return Map.class;
            }

            @Override
            public void handle(Envelope<Map> envelope) {
                throw new RuntimeException("Gateway HTTP 503 Service Unavailable");
            }
        };

        TenantAwareMessageListener listener = new TenantAwareMessageListener(
                tenantRegistry,
                processedEventRepository,
                outboxEventRepository,
                rabbitTemplate,
                objectMapper,
                transactionManager,
                List.of(failingConsumer));

        Envelope<Map<String, String>> env = new Envelope<>(
                "evt-fail-1", "USER_OTP_ISSUED", "school-a", "corr-fail",
                Instant.now(), Map.of("otp", "123456"));
        Message msg = MessageBuilder
                .withBody(objectMapper.writeValueAsString(env).getBytes(StandardCharsets.UTF_8))
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, "school-a")
                .build();

        listener.dispatchMessage(msg);

        assertTrue(TenantContext.get().isEmpty(), "TenantContext must be cleared even after consumer exception");
        assertEquals(3, outboxRow.getAttempts());
        assertEquals(OutboxStatus.DEAD_LETTERED, outboxRow.getStatus());
        assertTrue(outboxRow.getLastError().contains("503"));
        verify(rabbitTemplate, times(1)).send(
                eq(RabbitMqTopologyConfig.EXCHANGE_DLX),
                eq(RabbitMqTopologyConfig.ROUTING_DLQ),
                any(Message.class));
    }
}

