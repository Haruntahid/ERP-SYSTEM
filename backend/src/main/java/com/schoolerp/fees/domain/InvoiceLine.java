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
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "fee_head_id", nullable = false)
    private Long feeHeadId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "fine_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fineAmount;

    @Column(name = "net_payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPayableAmount;

    @Column(name = "allocated_paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "balance_due_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceDueAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected InvoiceLine() {}

    public InvoiceLine(Long invoiceId, Long feeHeadId, String description, BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal fineAmount) {
        this.invoiceId = invoiceId;
        this.feeHeadId = feeHeadId;
        this.description = description;
        this.grossAmount = grossAmount.setScale(2, RoundingMode.HALF_UP);
        this.discountAmount = (discountAmount != null ? discountAmount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.fineAmount = (fineAmount != null ? fineAmount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.netPayableAmount = this.grossAmount.subtract(this.discountAmount).add(this.fineAmount).setScale(2, RoundingMode.HALF_UP);
        this.allocatedPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.balanceDueAmount = this.netPayableAmount;
    }

    public Long getId() { return id; }
    public Long getInvoiceId() { return invoiceId; }
    public Long getFeeHeadId() { return feeHeadId; }
    public String getDescription() { return description; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getFineAmount() { return fineAmount; }
    public BigDecimal getNetPayableAmount() { return netPayableAmount; }
    public BigDecimal getAllocatedPaidAmount() { return allocatedPaidAmount; }
    public BigDecimal getBalanceDueAmount() { return balanceDueAmount; }

    public void allocate(BigDecimal amount) {
        this.allocatedPaidAmount = this.allocatedPaidAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
        this.balanceDueAmount = this.netPayableAmount.subtract(this.allocatedPaidAmount).setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }

    public void unallocate(BigDecimal amount) {
        this.allocatedPaidAmount = this.allocatedPaidAmount.subtract(amount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.balanceDueAmount = this.netPayableAmount.subtract(this.allocatedPaidAmount).setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }
}

