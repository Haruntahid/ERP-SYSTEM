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
@Table(name = "fee_structure_lines")
public class FeeStructureLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_structure_id", nullable = false)
    private Long feeStructureId;

    @Column(name = "fee_head_id", nullable = false)
    private Long feeHeadId;

    @Column(nullable = false, length = 20)
    private String frequency = "MONTHLY";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_day_of_month", nullable = false)
    private int dueDayOfMonth = 10;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FeeStructureLine() {}

    public FeeStructureLine(Long feeStructureId, Long feeHeadId, String frequency, BigDecimal amount, int dueDayOfMonth) {
        this.feeStructureId = feeStructureId;
        this.feeHeadId = feeHeadId;
        this.frequency = frequency != null && !frequency.isBlank() ? frequency.toUpperCase() : "MONTHLY";
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.dueDayOfMonth = dueDayOfMonth > 0 ? dueDayOfMonth : 10;
    }

    public Long getId() { return id; }
    public Long getFeeStructureId() { return feeStructureId; }
    public Long getFeeHeadId() { return feeHeadId; }
    public String getFrequency() { return frequency; }
    public BigDecimal getAmount() { return amount; }
    public int getDueDayOfMonth() { return dueDayOfMonth; }
    public boolean isMandatory() { return mandatory; }
}

