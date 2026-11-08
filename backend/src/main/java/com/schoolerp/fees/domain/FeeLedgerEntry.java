package com.schoolerp.fees.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "fee_ledger_entries")
public class FeeLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private Long enrollmentId;

    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private String entryType;

    @Column(name = "event_category", nullable = false, length = 40, updatable = false)
    private String eventCategory;

    @Column(name = "reference_type", nullable = false, length = 40, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "BDT";

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "running_balance_after", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal runningBalanceAfter;

    @Column(nullable = false, length = 500, updatable = false)
    private String narration;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FeeLedgerEntry() {}

    public FeeLedgerEntry(
            Long studentId,
            Long enrollmentId,
            String entryType,
            String eventCategory,
            String referenceType,
            Long referenceId,
            String currency,
            BigDecimal amount,
            BigDecimal runningBalanceAfter,
            String narration,
            Long actorUserId) {
        this.studentId = studentId;
        this.enrollmentId = enrollmentId;
        this.entryType = entryType;
        this.eventCategory = eventCategory;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.currency = currency != null ? currency : "BDT";
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.runningBalanceAfter = runningBalanceAfter.setScale(2, RoundingMode.HALF_UP);
        this.narration = narration;
        this.actorUserId = actorUserId;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getEnrollmentId() { return enrollmentId; }
    public String getEntryType() { return entryType; }
    public String getEventCategory() { return eventCategory; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public String getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRunningBalanceAfter() { return runningBalanceAfter; }
    public String getNarration() { return narration; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}

