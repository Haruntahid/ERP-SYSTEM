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
@Table(name = "payment_allocations")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "invoice_line_id", nullable = false)
    private Long invoiceLineId;

    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PaymentAllocation() {}

    public PaymentAllocation(Long paymentId, Long invoiceId, Long invoiceLineId, BigDecimal allocatedAmount) {
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.invoiceLineId = invoiceLineId;
        this.allocatedAmount = allocatedAmount.setScale(2, RoundingMode.HALF_UP);
        this.status = "ACTIVE";
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public Long getInvoiceId() { return invoiceId; }
    public Long getInvoiceLineId() { return invoiceLineId; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public String getStatus() { return status; }
    public void markReversed() {
        this.status = "REVERSED";
        this.updatedAt = Instant.now();
    }
}

