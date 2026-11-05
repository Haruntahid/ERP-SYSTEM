package com.schoolerp.staff.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "designations")
public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 20)
    private String category = "TEACHING";

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Designation() {}

    public Designation(String code, String title, String category) {
        this.code = code;
        this.title = title;
        this.category = category == null || category.isBlank() ? "TEACHING" : category;
        this.displayOrder = 1;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setTitle(String title) { this.title = title; this.updatedAt = Instant.now(); }
    public void setCategory(String category) { this.category = category; this.updatedAt = Instant.now(); }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

