package com.schoolerp.shared.messaging;

import java.time.Instant;

/**
 * Standard wire envelope for all asynchronous events published through the transactional outbox.
 */
public record Envelope<T>(
        String eventId,
        String eventType,
        String tenantSlug,
        String correlationId,
        Instant occurredAt,
        T payload) {
}

