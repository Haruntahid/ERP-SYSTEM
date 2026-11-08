package com.schoolerp.fees.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "fee_heads")
public class FeeHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String category = "TUITION";

    @Column(name = "allocation_priority", nullable = false)
    private int allocationPriority = 100;

    @Column(name = "is_refundable", nullable = false)
    private boolean refundable;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FeeHead() {}

    public FeeHead(String code, String name, String category, int allocationPriority, boolean refundable) {
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.category = category != null && !category.isBlank() ? category.trim().toUpperCase() : "TUITION";
        this.allocationPriority = allocationPriority > 0 ? allocationPriority : 100;
        this.refundable = refundable;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public String getCategory() { return category; }
    public int getAllocationPriority() { return allocationPriority; }
    public boolean isRefundable() { return refundable; }
    public String getStatus() { return status; }
}

