package com.schoolerp.shared.messaging;

/**
 * Pluggable domain event consumer contract. Implementations are automatically discovered by
 * {@link TenantAwareMessageListener} and invoked inside a tenant-scoped, idempotent transaction.
 *
 * @param <T> Deserialized event payload type
 */
public interface DomainEventConsumer<T> {

    /**
     * Logical consumer group name recorded in {@code processed_events.consumer_group} for idempotency.
     */
    String consumerGroup();

    /**
     * Domain event type handled by this consumer (e.g., {@code "FEE_PAYMENT_RECEIVED"}).
     */
    String eventType();

    /**
     * Java class used to deserialize {@link Envelope#payload()}.
     */
    Class<T> payloadType();

    /**
     * Processes the event inside an active tenant context and database transaction.
     */
    void handle(Envelope<T> envelope);
}

