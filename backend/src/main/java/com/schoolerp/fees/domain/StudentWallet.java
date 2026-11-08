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
@Table(name = "student_wallets")
public class StudentWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    @Column(name = "balance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentWallet() {}

    public StudentWallet(Long studentId, String currency) {
        this.studentId = studentId;
        this.currency = currency != null ? currency : "BDT";
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void credit(BigDecimal amount) {
        this.balanceAmount = this.balanceAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }
}

