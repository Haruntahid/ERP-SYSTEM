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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "class_subjects")
public class ClassSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private AcademicClass academicClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks = BigDecimal.valueOf(100);

    @Column(name = "pass_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal passMarks = BigDecimal.valueOf(33);

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ClassSubject() {}

    public ClassSubject(AcademicClass academicClass, Subject subject) {
        this.academicClass = academicClass;
        this.subject = subject;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public AcademicClass getAcademicClass() { return academicClass; }
    public Subject getSubject() { return subject; }
    public boolean isMandatory() { return mandatory; }
    public BigDecimal getMaxMarks() { return maxMarks; }
    public BigDecimal getPassMarks() { return passMarks; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; this.updatedAt = Instant.now(); }
    public void setMaxMarks(BigDecimal maxMarks) { this.maxMarks = maxMarks; this.updatedAt = Instant.now(); }
    public void setPassMarks(BigDecimal passMarks) { this.passMarks = passMarks; this.updatedAt = Instant.now(); }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

