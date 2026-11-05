package com.schoolerp.shared.messaging;

/**
 * Priority tier for outbox and RabbitMQ events.
 */
public enum EventPriority {
    HIGH(9),
    NORMAL(5),
    BULK(1);

    private final int amqpPriority;

    EventPriority(int amqpPriority) {
        this.amqpPriority = amqpPriority;
    }

    public int amqpPriority() {
        return amqpPriority;
    }
}

