package com.schoolerp.shared.messaging;

/**
 * Lifecycle state of a transactional outbox event row.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
    DEAD_LETTERED
}

