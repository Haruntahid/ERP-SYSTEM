package com.schoolerp.shared.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Central tenant-aware AMQP listener and consumer dispatcher.
 * Enforces strict {@code X-Tenant-Slug} header/envelope validation, binds {@link TenantContext}
 * inside a {@code try / finally} block, guarantees idempotency via {@code processed_events},
 * and routes failures through exponential backoff retry tiers to {@code erp.dlq}.
 */
@Component
public class TenantAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareMessageListener.class);
    private static final TypeReference<Envelope<JsonNode>> ENVELOPE_NODE_TYPE = new TypeReference<>() {
    };

    private final TenantRegistry tenantRegistry;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final List<DomainEventConsumer<?>> consumers;

    @Autowired
    public TenantAwareMessageListener(
            TenantRegistry tenantRegistry,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            List<DomainEventConsumer<?>> consumers) {
        this.tenantRegistry = tenantRegistry;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.consumers = consumers != null ? List.copyOf(consumers) : List.of();
    }

    @RabbitListener(queues = {
            RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_HIGH,
            RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_BULK,
            RabbitMqTopologyConfig.QUEUE_DOCUMENTS_GENERATE,
            RabbitMqTopologyConfig.QUEUE_IMPORTS_PROCESS
    })
    public void onAmqpMessage(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        boolean handled = dispatchMessage(message);
        if (channel != null && channel.isOpen()) {
            if (handled) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicReject(deliveryTag, false);
            }
        }
    }

    /**
     * Validates headers & envelope, binds {@link TenantContext}, and invokes registered
     * {@link DomainEventConsumer} implementations idempotently.
     *
     * @return {@code true} if the message was processed (or idempotently skipped / routed to retry/DLQ),
     *         {@code false} if rejected due to cross-tenant header/envelope violation or invalid tenant.
     */
    public boolean dispatchMessage(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        String headerTenantSlug = stringHeader(headers, OutboxEventWriter.HEADER_TENANT_SLUG);

        Envelope<JsonNode> rawEnvelope;
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            rawEnvelope = objectMapper.readValue(body, ENVELOPE_NODE_TYPE);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to deserialize AMQP message envelope; routing to DLQ: {}", e.getMessage());
            publishToDlq(message, "UNPARSEABLE_ENVELOPE");
            return false;
        }

        if (headerTenantSlug == null || headerTenantSlug.isBlank()
                || rawEnvelope == null
                || rawEnvelope.tenantSlug() == null
                || !headerTenantSlug.equals(rawEnvelope.tenantSlug())) {
            log.error(
                    "CRITICAL: Tenant isolation violation on AMQP message eventId={}: X-Tenant-Slug='{}' vs envelope.tenantSlug='{}'",
                    rawEnvelope != null ? rawEnvelope.eventId() : "unknown",
                    headerTenantSlug,
                    rawEnvelope != null ? rawEnvelope.tenantSlug() : "null");
            publishToDlq(message, "TENANT_HEADER_MISMATCH");
            return false;
        }

        Optional<TenantInfo> tenantOpt = tenantRegistry.find(headerTenantSlug);
        if (tenantOpt.isEmpty() || tenantOpt.get().status() != TenantStatus.ACTIVE) {
            log.error(
                    "CRITICAL: Rejecting AMQP message eventId={} for unknown or inactive tenant '{}'",
                    rawEnvelope.eventId(),
                    headerTenantSlug);
            publishToDlq(message, "INACTIVE_OR_UNKNOWN_TENANT");
            return false;
        }

        List<DomainEventConsumer<?>> matchingConsumers = consumers.stream()
                .filter(c -> c.eventType().equals(rawEnvelope.eventType()))
                .toList();

        if (matchingConsumers.isEmpty()) {
            return true;
        }

        try {
            TenantContext.set(headerTenantSlug);
            MDC.put("tenantSlug", headerTenantSlug);
            if (rawEnvelope.correlationId() != null) {
                MDC.put("correlationId", rawEnvelope.correlationId());
            }

            for (DomainEventConsumer<?> consumer : matchingConsumers) {
                invokeConsumerIdempotently(consumer, rawEnvelope, message);
            }
            return true;
        } finally {
            MDC.remove("tenantSlug");
            MDC.remove("correlationId");
            TenantContext.clear();
        }
    }

    private <T> void invokeConsumerIdempotently(
            DomainEventConsumer<T> consumer,
            Envelope<JsonNode> rawEnvelope,
            Message originalMessage) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (processedEventRepository.existsByEventIdAndConsumerGroup(
                        rawEnvelope.eventId(), consumer.consumerGroup())) {
                    log.debug(
                            "Skipping duplicate delivery of eventId={} for consumerGroup={}",
                            rawEnvelope.eventId(),
                            consumer.consumerGroup());
                    return;
                }

                T typedPayload = objectMapper.convertValue(rawEnvelope.payload(), consumer.payloadType());
                Envelope<T> typedEnvelope = new Envelope<>(
                        rawEnvelope.eventId(),
                        rawEnvelope.eventType(),
                        rawEnvelope.tenantSlug(),
                        rawEnvelope.correlationId(),
                        rawEnvelope.occurredAt(),
                        typedPayload);

                consumer.handle(typedEnvelope);

                try {
                    processedEventRepository.saveAndFlush(new ProcessedEvent(
                            rawEnvelope.eventId(),
                            consumer.consumerGroup(),
                            rawEnvelope.eventType(),
                            Instant.now()));
                } catch (DataIntegrityViolationException duplicate) {
                    // Concurrent duplicate delivery caught by unique constraint (event_id, consumer_group)
                    status.setRollbackOnly();
                }
            });
        } catch (Exception ex) {
            handleConsumerFailure(rawEnvelope, originalMessage, ex);
        }
    }

    private void handleConsumerFailure(Envelope<JsonNode> envelope, Message originalMessage, Exception ex) {
        log.warn(
                "Consumer failed for tenant={} eventId={} eventType={}: {}",
                envelope.tenantSlug(),
                envelope.eventId(),
                envelope.eventType(),
                ex.getMessage());

        transactionTemplate.executeWithoutResult(status -> {
            Optional<OutboxEvent> outboxOpt = outboxEventRepository.findByEventId(envelope.eventId());
            int attempt = 1;
            int maxAttempts = 3;

            if (outboxOpt.isPresent()) {
                OutboxEvent outbox = outboxOpt.get();
                attempt = outbox.getAttempts() + 1;
                maxAttempts = outbox.getMaxAttempts() > 0 ? outbox.getMaxAttempts() : 3;
                outbox.setAttempts(attempt);
                String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                outbox.setLastError(err.length() > 1000 ? err.substring(0, 1000) : err);
                Instant now = Instant.now();
                outbox.setUpdatedAt(now);
                if (attempt >= maxAttempts) {
                    outbox.setStatus(OutboxStatus.DEAD_LETTERED);
                } else {
                    outbox.setStatus(OutboxStatus.FAILED);
                    outbox.setNextRetryAt(now.plusSeconds(OutboxPublisher.backoffSecondsForAttempt(attempt)));
                }
                outboxEventRepository.save(outbox);
            }

            if (attempt >= maxAttempts) {
                publishToDlq(originalMessage, ex.getMessage());
            } else {
                String retryRoutingKey = switch (attempt) {
                    case 1 -> RabbitMqTopologyConfig.ROUTING_RETRY_5S;
                    case 2 -> RabbitMqTopologyConfig.ROUTING_RETRY_30S;
                    default -> RabbitMqTopologyConfig.ROUTING_RETRY_5M;
                };
                try {
                    rabbitTemplate.send(RabbitMqTopologyConfig.EXCHANGE_DLX, retryRoutingKey, originalMessage);
                } catch (Exception ignored) {
                    // Outbox row is already marked FAILED with nextRetryAt so OutboxPublisher will retry
                }
            }
        });
    }

    private void publishToDlq(Message originalMessage, String reason) {
        try {
            Message dlqMessage = MessageBuilder.fromMessage(originalMessage)
                    .setHeader("X-DLQ-Reason", reason != null ? reason : "UNKNOWN")
                    .build();
            rabbitTemplate.send(
                    RabbitMqTopologyConfig.EXCHANGE_DLX,
                    RabbitMqTopologyConfig.ROUTING_DLQ,
                    dlqMessage);
        } catch (Exception e) {
            log.debug("Could not forward rejected message to DLQ exchange: {}", e.getMessage());
        }
    }

    private static String stringHeader(Map<String, Object> headers, String key) {
        if (headers == null) {
            return null;
        }
        Object val = headers.get(key);
        return val != null ? val.toString() : null;
    }
}

