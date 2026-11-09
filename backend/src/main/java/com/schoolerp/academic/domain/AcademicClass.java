package com.schoolerp.academic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "academic_classes")
public class AcademicClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "numeric_level", nullable = false)
    private int numericLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_class_id")
    private AcademicClass nextClass;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AcademicClass() {}

    public AcademicClass(Department department, String code, String name, int numericLevel) {
        this.department = department;
        this.code = code;
        this.name = name;
        this.numericLevel = numericLevel;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Department getDepartment() { return department; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getNumericLevel() { return numericLevel; }
    public AcademicClass getNextClass() { return nextClass; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setDepartment(Department department) { this.department = department; this.updatedAt = Instant.now(); }
    public void setCode(String code) { this.code = code; this.updatedAt = Instant.now(); }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public void setNumericLevel(int numericLevel) { this.numericLevel = numericLevel; this.updatedAt = Instant.now(); }
    public void setNextClass(AcademicClass nextClass) { this.nextClass = nextClass; this.updatedAt = Instant.now(); }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

