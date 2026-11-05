package com.schoolerp.staff.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "teacher_subject_assignments")
public class TeacherSubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_session_id", nullable = false)
    private Long academicSessionId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "is_class_teacher", nullable = false)
    private boolean classTeacher = false;

    @Column(name = "role_type", nullable = false, length = 20)
    private String roleType = "PRIMARY";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TeacherSubjectAssignment() {}

    public TeacherSubjectAssignment(Long academicSessionId, Long staffId, Long sectionId, Long subjectId, boolean classTeacher, String roleType) {
        this.academicSessionId = academicSessionId;
        this.staffId = staffId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.classTeacher = classTeacher;
        this.roleType = roleType == null || roleType.isBlank() ? "PRIMARY" : roleType;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getAcademicSessionId() { return academicSessionId; }
    public Long getStaffId() { return staffId; }
    public Long getSectionId() { return sectionId; }
    public Long getSubjectId() { return subjectId; }
    public boolean isClassTeacher() { return classTeacher; }
    public String getRoleType() { return roleType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; this.updatedAt = Instant.now(); }
    public void setClassTeacher(boolean classTeacher) { this.classTeacher = classTeacher; this.updatedAt = Instant.now(); }
    public void setRoleType(String roleType) { this.roleType = roleType; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

