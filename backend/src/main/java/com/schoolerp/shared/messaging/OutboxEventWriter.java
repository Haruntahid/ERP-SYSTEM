package com.schoolerp.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.tenancy.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Persists domain events into the active tenant's {@code outbox_events} table inside
 * the caller's database transaction so business state and event emission commit or roll back atomically.
 */
@Component
public class OutboxEventWriter {

    public static final String HEADER_TENANT_SLUG = "X-Tenant-Slug";
    public static final String HEADER_EVENT_ID = "X-Event-ID";
    public static final String HEADER_EVENT_TYPE = "X-Event-Type";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    private final OutboxEventRepository outboxEventRepository;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(
            OutboxEventRepository outboxEventRepository,
            SettingsService settingsService,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public <T> OutboxEvent write(
            String aggregateType,
            String aggregateId,
            String eventType,
            String routingKey,
            EventPriority priority,
            T payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "OutboxEventWriter.write(...) must be invoked inside an active @Transactional boundary");
        }
        String tenantSlug = TenantContext.require();
        String eventId = UUID.randomUUID().toString();
        String correlationId = resolveCorrelationId(eventId);
        Instant now = Instant.now();

        Envelope<T> envelope = new Envelope<>(
                eventId,
                eventType,
                tenantSlug,
                correlationId,
                now,
                payload);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_TENANT_SLUG, tenantSlug);
        headers.put(HEADER_EVENT_ID, eventId);
        headers.put(HEADER_EVENT_TYPE, eventType);
        headers.put(HEADER_CORRELATION_ID, correlationId);

        int maxAttempts = settingsService != null
                ? Math.max(1, settingsService.getInt("outbox.max_attempts", 3))
                : 3;

        OutboxEvent row = new OutboxEvent();
        row.setEventId(eventId);
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setEventType(eventType);
        row.setRoutingKey(routingKey);
        row.setPriority(priority != null ? priority : EventPriority.NORMAL);
        row.setPayloadJson(toJson(envelope));
        row.setHeadersJson(toJson(headers));
        row.setStatus(OutboxStatus.PENDING);
        row.setAttempts(0);
        row.setMaxAttempts(maxAttempts);
        row.setNextRetryAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        return outboxEventRepository.save(row);
    }

    private String resolveCorrelationId(String fallbackEventId) {
        String mdcId = MDC.get("correlationId");
        if (mdcId != null && !mdcId.isBlank()) {
            return mdcId;
        }
        return fallbackEventId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}

