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
@Table(name = "student_discount_waivers")
public class StudentDiscountWaiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "fee_head_id", nullable = false)
    private Long feeHeadId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Column(nullable = false, length = 25)
    private String status = "ACTIVE";

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected StudentDiscountWaiver() {}

    public StudentDiscountWaiver(
            Long enrollmentId,
            Long studentId,
            Long feeHeadId,
            String title,
            String discountType,
            BigDecimal discountValue,
            String reason,
            Long requestedByUserId,
            boolean autoApprove) {
        this.enrollmentId = enrollmentId;
        this.studentId = studentId;
        this.feeHeadId = feeHeadId;
        this.title = title;
        this.discountType = discountType != null ? discountType.toUpperCase() : "FIXED_AMOUNT";
        this.discountValue = discountValue.setScale(2, RoundingMode.HALF_UP);
        this.reason = reason;
        this.requestedByUserId = requestedByUserId != null ? requestedByUserId : 1L;
        if (autoApprove) {
            this.status = "ACTIVE";
            this.approvedByUserId = this.requestedByUserId;
            this.approvedAt = Instant.now();
        } else {
            this.status = "PENDING_APPROVAL";
        }
    }

    public Long getId() { return id; }
    public Long getEnrollmentId() { return enrollmentId; }
    public Long getStudentId() { return studentId; }
    public Long getFeeHeadId() { return feeHeadId; }
    public String getTitle() { return title; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public void approve(Long approverUserId) {
        this.status = "ACTIVE";
        this.approvedByUserId = approverUserId;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}

