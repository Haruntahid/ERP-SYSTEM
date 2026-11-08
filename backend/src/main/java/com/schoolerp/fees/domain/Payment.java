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
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, length = 40, unique = true)
    private String receiptNo;

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "gateway_code", nullable = false, length = 30)
    private String gatewayCode;

    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    @Column(name = "bank_or_cheque_ref", length = 120)
    private String bankOrChequeRef;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status = "SUCCEEDED";

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "collected_by_user_id")
    private Long collectedByUserId;

    @Column(name = "verification_token", nullable = false, length = 80, unique = true)
    private String verificationToken;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "reversed_by_user_id")
    private Long reversedByUserId;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Payment() {}

    public Payment(
            String receiptNo,
            String idempotencyKey,
            Long studentId,
            Long enrollmentId,
            String gatewayCode,
            String bankOrChequeRef,
            String currency,
            BigDecimal amount,
            Long collectedByUserId,
            String verificationToken) {
        this.receiptNo = receiptNo;
        this.idempotencyKey = idempotencyKey;
        this.studentId = studentId;
        this.enrollmentId = enrollmentId;
        this.gatewayCode = gatewayCode != null ? gatewayCode.toUpperCase() : "OFFLINE_CASH";
        this.bankOrChequeRef = bankOrChequeRef;
        this.currency = currency != null ? currency : "BDT";
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.status = "SUCCEEDED";
        this.paidAt = Instant.now();
        this.collectedByUserId = collectedByUserId;
        this.verificationToken = verificationToken;
    }

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getStudentId() { return studentId; }
    public Long getEnrollmentId() { return enrollmentId; }
    public String getGatewayCode() { return gatewayCode; }
    public String getBankOrChequeRef() { return bankOrChequeRef; }
    public String getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public String getVerificationToken() { return verificationToken; }
    public String getReversalReason() { return reversalReason; }

    public void markReversed(String reason, Long reversedByUserId) {
        this.status = "REVERSED";
        this.reversalReason = reason;
        this.reversedByUserId = reversedByUserId;
        this.reversedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}

