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
import java.time.LocalDate;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, length = 40, unique = true)
    private String invoiceNo;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "academic_session_id", nullable = false)
    private Long academicSessionId;

    @Column(name = "billing_month", length = 7)
    private String billingMonth;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "fine_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fineAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "net_payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPayableAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "allocated_paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "balance_due_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceDueAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(nullable = false, length = 25)
    private String status = "ISSUED";

    @Column(name = "generation_batch_key", length = 80)
    private String generationBatchKey;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Invoice() {}

    public Invoice(
            String invoiceNo,
            Long enrollmentId,
            Long studentId,
            Long academicSessionId,
            String billingMonth,
            LocalDate issueDate,
            LocalDate dueDate,
            String currency,
            Long createdByUserId) {
        this.invoiceNo = invoiceNo;
        this.enrollmentId = enrollmentId;
        this.studentId = studentId;
        this.academicSessionId = academicSessionId;
        this.billingMonth = billingMonth;
        this.issueDate = issueDate != null ? issueDate : LocalDate.now();
        this.dueDate = dueDate != null ? dueDate : LocalDate.now().plusDays(15);
        this.currency = currency != null && !currency.isBlank() ? currency : "BDT";
        this.createdByUserId = createdByUserId;
        this.status = "ISSUED";
    }

    public Long getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public Long getEnrollmentId() { return enrollmentId; }
    public Long getStudentId() { return studentId; }
    public Long getAcademicSessionId() { return academicSessionId; }
    public String getBillingMonth() { return billingMonth; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public String getCurrency() { return currency; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getFineAmount() { return fineAmount; }
    public BigDecimal getNetPayableAmount() { return netPayableAmount; }
    public BigDecimal getAllocatedPaidAmount() { return allocatedPaidAmount; }
    public BigDecimal getBalanceDueAmount() { return balanceDueAmount; }
    public String getStatus() { return status; }

    public void recalculateFromLines(BigDecimal gross, BigDecimal discount, BigDecimal fine, BigDecimal paid) {
        this.grossAmount = gross.setScale(2, RoundingMode.HALF_UP);
        this.discountAmount = discount.setScale(2, RoundingMode.HALF_UP);
        this.fineAmount = fine.setScale(2, RoundingMode.HALF_UP);
        this.netPayableAmount = this.grossAmount.subtract(this.discountAmount).add(this.fineAmount).setScale(2, RoundingMode.HALF_UP);
        this.allocatedPaidAmount = paid.setScale(2, RoundingMode.HALF_UP);
        this.balanceDueAmount = this.netPayableAmount.subtract(this.allocatedPaidAmount).setScale(2, RoundingMode.HALF_UP);

        if ("CANCELLED".equals(this.status)) {
            return;
        }
        if (this.balanceDueAmount.compareTo(BigDecimal.ZERO) <= 0 && this.netPayableAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.status = "PAID";
        } else if (this.allocatedPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.status = "PARTIALLY_PAID";
        } else {
            this.status = "ISSUED";
        }
        this.updatedAt = Instant.now();
    }
}

