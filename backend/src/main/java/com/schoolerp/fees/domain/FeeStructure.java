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
@Table(name = "fee_structures")
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_session_id", nullable = false)
    private Long academicSessionId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Column(name = "late_fine_policy_type", nullable = false, length = 30)
    private String lateFinePolicyType = "NONE";

    @Column(name = "late_fine_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFineValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "late_fine_cap_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFineCapAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "late_fine_grace_days", nullable = false)
    private int lateFineGraceDays;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FeeStructure() {}

    public FeeStructure(Long academicSessionId, Long campusId, Long classId, String name, String currency) {
        this.academicSessionId = academicSessionId;
        this.campusId = campusId != null ? campusId : 1L;
        this.classId = classId;
        this.name = name.trim();
        this.currency = currency != null && !currency.isBlank() ? currency.trim().toUpperCase() : "BDT";
    }

    public Long getId() { return id; }
    public Long getAcademicSessionId() { return academicSessionId; }
    public Long getCampusId() { return campusId; }
    public Long getClassId() { return classId; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public String getLateFinePolicyType() { return lateFinePolicyType; }
    public BigDecimal getLateFineValue() { return lateFineValue; }
    public String getStatus() { return status; }
}

