package com.schoolerp.shared.messaging;

import com.schoolerp.shared.messaging.OutboxMessagingService.BatchReplayRequestDto;
import com.schoolerp.shared.messaging.OutboxMessagingService.BatchReplayResultDto;
import com.schoolerp.shared.messaging.OutboxMessagingService.OutboxEventDetailDto;
import com.schoolerp.shared.messaging.OutboxMessagingService.OutboxEventSummaryDto;
import com.schoolerp.shared.messaging.OutboxMessagingService.OutboxStatsDto;
import com.schoolerp.shared.messaging.OutboxMessagingService.PageResponse;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped REST API for inspecting transactional outbox events, delivery statistics,
 * and replaying failed or dead-lettered events.
 */
@RestController
@RequestMapping("/api/v1/messaging")
public class OutboxMessagingController {

    private final OutboxMessagingService outboxMessagingService;

    public OutboxMessagingController(OutboxMessagingService outboxMessagingService) {
        this.outboxMessagingService = outboxMessagingService;
    }

    @GetMapping("/outbox")
    @PreAuthorize("@perm.has(authentication, 'messaging.read')")
    public PageResponse<OutboxEventSummaryDto> listOutboxEvents(
            @RequestParam(required = false) OutboxStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return outboxMessagingService.listOutboxEvents(status, eventType, aggregateType, from, to, page, size);
    }

    @GetMapping("/outbox/{eventId}")
    @PreAuthorize("@perm.has(authentication, 'messaging.read')")
    public OutboxEventDetailDto getOutboxEvent(@PathVariable String eventId) {
        return outboxMessagingService.getOutboxEvent(eventId);
    }

    @PostMapping("/outbox/{eventId}/replay")
    @PreAuthorize("@perm.has(authentication, 'messaging.replay')")
    public OutboxEventSummaryDto replayEvent(@PathVariable String eventId) {
        return outboxMessagingService.replayEvent(eventId);
    }

    @PostMapping("/outbox/replay-batch")
    @PreAuthorize("@perm.has(authentication, 'messaging.replay')")
    public BatchReplayResultDto replayBatch(@RequestBody BatchReplayRequestDto request) {
        return outboxMessagingService.replayBatch(request != null ? request.eventIds() : java.util.List.of());
    }

    @GetMapping("/stats")
    @PreAuthorize("@perm.has(authentication, 'messaging.read')")
    public OutboxStatsDto getStats() {
        return outboxMessagingService.getStats();
    }
}

