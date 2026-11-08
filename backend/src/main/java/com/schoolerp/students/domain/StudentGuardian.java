package com.schoolerp.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "student_guardians")
public class StudentGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    @Column(nullable = false, length = 30)
    private String relationship;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "is_emergency_contact", nullable = false)
    private boolean emergencyContact = true;

    @Column(name = "can_pickup", nullable = false)
    private boolean canPickup = true;

    @Column(name = "is_fee_payer", nullable = false)
    private boolean feePayer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected StudentGuardian() {}

    public StudentGuardian(Long studentId, Long guardianId, String relationship, boolean primaryContact, boolean feePayer) {
        this.studentId = studentId;
        this.guardianId = guardianId;
        this.relationship = relationship != null && !relationship.isBlank() ? relationship.toUpperCase() : "FATHER";
        this.primaryContact = primaryContact;
        this.feePayer = feePayer;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getGuardianId() { return guardianId; }
    public String getRelationship() { return relationship; }
    public boolean isPrimaryContact() { return primaryContact; }
    public boolean isEmergencyContact() { return emergencyContact; }
    public boolean isCanPickup() { return canPickup; }
    public boolean isFeePayer() { return feePayer; }
}

