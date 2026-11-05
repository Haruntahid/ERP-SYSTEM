package com.schoolerp.shared.messaging;

import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped service for inspecting outbox events, querying health statistics,
 * and replaying failed or dead-lettered events.
 */
@Service
public class OutboxMessagingService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonPayloadMasker payloadMasker;
    private final AuditService auditService;

    public OutboxMessagingService(
            OutboxEventRepository outboxEventRepository,
            JsonPayloadMasker payloadMasker,
            AuditService auditService) {
        this.outboxEventRepository = outboxEventRepository;
        this.payloadMasker = payloadMasker;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OutboxEventSummaryDto> listOutboxEvents(
            OutboxStatus status,
            String eventType,
            String aggregateType,
            Instant from,
            Instant to,
            int page,
            int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normEventType = normalizeFilter(eventType);
        String normAggregateType = normalizeFilter(aggregateType);

        Page<OutboxEvent> result = outboxEventRepository.searchOutboxEvents(
                status,
                normEventType,
                normAggregateType,
                from,
                to,
                PageRequest.of(safePage, safeSize));

        List<OutboxEventSummaryDto> items = result.getContent().stream()
                .map(OutboxEventSummaryDto::from)
                .toList();

        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OutboxEventDetailDto getOutboxEvent(String eventId) {
        OutboxEvent event = outboxEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        return new OutboxEventDetailDto(
                event.getEventId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getRoutingKey(),
                event.getPriority(),
                event.getStatus(),
                event.getAttempts(),
                event.getMaxAttempts(),
                event.getNextRetryAt(),
                event.getLastError(),
                payloadMasker.maskJson(event.getPayloadJson()),
                payloadMasker.maskJson(event.getHeadersJson()),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getUpdatedAt());
    }

    @Transactional
    public OutboxEventSummaryDto replayEvent(String eventId) {
        OutboxEvent event = outboxEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Outbox event not found"));

        if (event.getStatus() != OutboxStatus.FAILED && event.getStatus() != OutboxStatus.DEAD_LETTERED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only FAILED or DEAD_LETTERED outbox events can be replayed");
        }

        OutboxStatus previousStatus = event.getStatus();
        int previousAttempts = event.getAttempts();
        Instant now = Instant.now();

        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextRetryAt(now);
        event.setLastError(null);
        event.setUpdatedAt(now);
        OutboxEvent saved = outboxEventRepository.save(event);

        auditService.record(
                "OUTBOX_EVENT_REPLAYED",
                "OUTBOX_EVENT",
                event.getEventId(),
                Map.of("status", previousStatus.name(), "attempts", previousAttempts),
                Map.of("status", OutboxStatus.PENDING.name(), "attempts", 0));

        return OutboxEventSummaryDto.from(saved);
    }

    @Transactional
    public BatchReplayResultDto replayBatch(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new BatchReplayResultDto(0, 0);
        }
        if (eventIds.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot replay more than 100 events in a single batch");
        }

        List<OutboxEvent> events = outboxEventRepository.findByEventIdIn(eventIds);
        int replayedCount = 0;
        int skippedCount = 0;
        Instant now = Instant.now();

        for (OutboxEvent event : events) {
            if (event.getStatus() == OutboxStatus.FAILED || event.getStatus() == OutboxStatus.DEAD_LETTERED) {
                OutboxStatus prevStatus = event.getStatus();
                event.setStatus(OutboxStatus.PENDING);
                event.setAttempts(0);
                event.setNextRetryAt(now);
                event.setLastError(null);
                event.setUpdatedAt(now);
                outboxEventRepository.save(event);

                auditService.record(
                        "OUTBOX_EVENT_REPLAYED",
                        "OUTBOX_EVENT",
                        event.getEventId(),
                        Map.of("status", prevStatus.name()),
                        Map.of("status", OutboxStatus.PENDING.name()));
                replayedCount++;
            } else {
                skippedCount++;
            }
        }
        return new BatchReplayResultDto(replayedCount, skippedCount);
    }

    @Transactional(readOnly = true)
    public OutboxStatsDto getStats() {
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long failed = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long deadLettered = outboxEventRepository.countByStatus(OutboxStatus.DEAD_LETTERED);
        long publishedLast24h = outboxEventRepository.countPublishedSince(Instant.now().minus(24, ChronoUnit.HOURS));

        Optional<Instant> oldestPending = outboxEventRepository.findOldestPendingCreatedAt();
        Long oldestAgeSeconds = oldestPending
                .map(createdAt -> Math.max(0L, Duration.between(createdAt, Instant.now()).getSeconds()))
                .orElse(null);

        return new OutboxStatsDto(pending, failed, deadLettered, publishedLast24h, oldestAgeSeconds);
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record OutboxEventSummaryDto(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String routingKey,
            EventPriority priority,
            OutboxStatus status,
            int attempts,
            int maxAttempts,
            Instant nextRetryAt,
            String lastError,
            Instant createdAt,
            Instant publishedAt) {

        public static OutboxEventSummaryDto from(OutboxEvent e) {
            return new OutboxEventSummaryDto(
                    e.getEventId(),
                    e.getAggregateType(),
                    e.getAggregateId(),
                    e.getEventType(),
                    e.getRoutingKey(),
                    e.getPriority(),
                    e.getStatus(),
                    e.getAttempts(),
                    e.getMaxAttempts(),
                    e.getNextRetryAt(),
                    e.getLastError(),
                    e.getCreatedAt(),
                    e.getPublishedAt());
        }
    }

    public record OutboxEventDetailDto(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String routingKey,
            EventPriority priority,
            OutboxStatus status,
            int attempts,
            int maxAttempts,
            Instant nextRetryAt,
            String lastError,
            String payloadJson,
            String headersJson,
            Instant createdAt,
            Instant publishedAt,
            Instant updatedAt) {
    }

    public record BatchReplayRequestDto(List<String> eventIds) {
    }

    public record BatchReplayResultDto(int replayedCount, int skippedCount) {
    }

    public record OutboxStatsDto(
            long pendingCount,
            long failedCount,
            long deadLetteredCount,
            long publishedLast24hCount,
            Long oldestPendingAgeSeconds) {
    }
}

