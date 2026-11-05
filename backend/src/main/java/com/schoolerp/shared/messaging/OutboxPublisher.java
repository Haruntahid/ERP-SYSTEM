package com.schoolerp.shared.messaging;

import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.cache.TenantDistributedLock;
import com.schoolerp.shared.tenancy.MasterTenantRepository;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled relay that polls active school tenants, claims pending outbox events with
 * {@code FOR UPDATE SKIP LOCKED}, publishes them to RabbitMQ with mandatory tenant headers,
 * and marks rows {@code PUBLISHED} or transitions failed rows with exponential backoff.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Duration POLL_LOCK_TTL = Duration.ofSeconds(15);

    private final MasterTenantRepository masterTenantRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final SettingsService settingsService;
    private final TenantDistributedLock distributedLock;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(
            MasterTenantRepository masterTenantRepository,
            OutboxEventRepository outboxEventRepository,
            ProcessedEventRepository processedEventRepository,
            SettingsService settingsService,
            TenantDistributedLock distributedLock,
            RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        this.masterTenantRepository = masterTenantRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.settingsService = settingsService;
        this.distributedLock = distributedLock;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.messaging.relay-poll-ms:1000}")
    public void pollAllActiveTenants() {
        List<TenantInfo> tenants;
        try {
            tenants = masterTenantRepository.findAll();
        } catch (Exception e) {
            log.debug("Skipping outbox relay cycle because master tenant catalog is unavailable: {}", e.getMessage());
            return;
        }

        for (TenantInfo tenant : tenants) {
            if (tenant.status() != TenantStatus.ACTIVE) {
                continue;
            }
            relayTenantOutbox(tenant.slug());
        }
    }

    public int relayTenantOutbox(String tenantSlug) {
        String ownerToken = UUID.randomUUID().toString();
        if (!distributedLock.tryLock(tenantSlug, "outbox:poll", ownerToken, POLL_LOCK_TTL)) {
            return 0;
        }
        try {
            TenantContext.set(tenantSlug);
            Integer count = transactionTemplate.execute(status -> claimAndPublishBatch(tenantSlug));
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Outbox relay poll failed for tenant {}: {}", tenantSlug, e.getMessage());
            return 0;
        } finally {
            TenantContext.clear();
            distributedLock.unlock(tenantSlug, "outbox:poll", ownerToken);
        }
    }

    protected int claimAndPublishBatch(String tenantSlug) {
        int batchSize = Math.max(1, settingsService.getInt("outbox.batch_size", 50));
        int defaultMaxAttempts = Math.max(1, settingsService.getInt("outbox.max_attempts", 3));
        Instant now = Instant.now();

        List<OutboxEvent> batch = outboxEventRepository.claimNextBatchForRelay(now, batchSize);
        int publishedCount = 0;

        for (OutboxEvent event : batch) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setUpdatedAt(now);
            try {
                Message amqpMessage = buildAmqpMessage(tenantSlug, event);
                rabbitTemplate.send(
                        RabbitMqTopologyConfig.EXCHANGE_EVENTS_TOPIC,
                        event.getRoutingKey(),
                        amqpMessage);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                event.setUpdatedAt(Instant.now());
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception ex) {
                handlePublishFailure(event, defaultMaxAttempts, ex);
                outboxEventRepository.save(event);
            }
        }
        return publishedCount;
    }

    private Message buildAmqpMessage(String tenantSlug, OutboxEvent event) {
        EventPriority priority = event.getPriority() != null ? event.getPriority() : EventPriority.NORMAL;
        return MessageBuilder
                .withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getEventId())
                .setPriority(priority.amqpPriority())
                .setHeader(OutboxEventWriter.HEADER_TENANT_SLUG, tenantSlug)
                .setHeader(OutboxEventWriter.HEADER_EVENT_ID, event.getEventId())
                .setHeader(OutboxEventWriter.HEADER_EVENT_TYPE, event.getEventType())
                .setHeader(OutboxEventWriter.HEADER_CORRELATION_ID, event.getEventId())
                .build();
    }

    private void handlePublishFailure(OutboxEvent event, int defaultMaxAttempts, Exception ex) {
        int nextAttempts = event.getAttempts() + 1;
        int maxAttempts = event.getMaxAttempts() > 0 ? event.getMaxAttempts() : defaultMaxAttempts;
        event.setAttempts(nextAttempts);
        String errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        event.setLastError(errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
        Instant now = Instant.now();
        event.setUpdatedAt(now);

        if (nextAttempts >= maxAttempts) {
            event.setStatus(OutboxStatus.DEAD_LETTERED);
        } else {
            event.setStatus(OutboxStatus.FAILED);
            event.setNextRetryAt(now.plusSeconds(backoffSecondsForAttempt(nextAttempts)));
        }
    }

    public static long backoffSecondsForAttempt(int attempt) {
        return switch (attempt) {
            case 1 -> 5L;
            case 2 -> 30L;
            default -> 300L;
        };
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purgeExpiredEventsAllTenants() {
        List<TenantInfo> tenants;
        try {
            tenants = masterTenantRepository.findAll();
        } catch (Exception e) {
            return;
        }
        for (TenantInfo tenant : tenants) {
            if (tenant.status() != TenantStatus.ACTIVE) {
                continue;
            }
            purgeExpiredEventsForTenant(tenant.slug());
        }
    }

    public int purgeExpiredEventsForTenant(String tenantSlug) {
        try {
            TenantContext.set(tenantSlug);
            Integer deleted = transactionTemplate.execute(status -> {
                int retentionDays = Math.max(1, settingsService.getInt("outbox.retention_days", 30));
                Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
                int outboxDeleted = outboxEventRepository.deletePublishedBefore(cutoff);
                int inboxDeleted = processedEventRepository.deleteProcessedBefore(cutoff);
                return outboxDeleted + inboxDeleted;
            });
            return deleted != null ? deleted : 0;
        } finally {
            TenantContext.clear();
        }
    }
}
