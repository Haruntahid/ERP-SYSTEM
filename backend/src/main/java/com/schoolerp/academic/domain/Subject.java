package com.schoolerp.academic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType = "THEORY";

    @Column(name = "credit_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal creditHours = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Subject() {}

    public Subject(String code, String name, String subjectType, BigDecimal creditHours) {
        this.code = code;
        this.name = name;
        this.subjectType = subjectType == null || subjectType.isBlank() ? "THEORY" : subjectType;
        this.creditHours = creditHours == null ? BigDecimal.ONE : creditHours;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSubjectType() { return subjectType; }
    public BigDecimal getCreditHours() { return creditHours; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setCode(String code) { this.code = code; this.updatedAt = Instant.now(); }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; this.updatedAt = Instant.now(); }
    public void setCreditHours(BigDecimal creditHours) { this.creditHours = creditHours; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

