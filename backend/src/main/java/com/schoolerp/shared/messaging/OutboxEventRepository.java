package com.schoolerp.shared.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByEventIdIn(Collection<String> eventIds);

    @Query(
            value = """
                    SELECT * FROM outbox_events
                    WHERE status IN ('PENDING', 'FAILED')
                      AND next_retry_at <= :now
                    ORDER BY
                      CASE priority
                        WHEN 'HIGH' THEN 1
                        WHEN 'NORMAL' THEN 2
                        ELSE 3
                      END ASC,
                      id ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<OutboxEvent> claimNextBatchForRelay(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:eventType IS NULL OR e.eventType = :eventType)
              AND (:aggregateType IS NULL OR e.aggregateType = :aggregateType)
              AND (:fromTime IS NULL OR e.createdAt >= :fromTime)
              AND (:toTime IS NULL OR e.createdAt <= :toTime)
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    Page<OutboxEvent> searchOutboxEvents(
            @Param("status") OutboxStatus status,
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt >= :since")
    long countPublishedSince(@Param("since") Instant since);

    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status IN ('PENDING', 'FAILED')")
    Optional<Instant> findOldestPendingCreatedAt();

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}

