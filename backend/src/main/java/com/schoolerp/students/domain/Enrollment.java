package com.schoolerp.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "academic_session_id", nullable = false)
    private Long academicSessionId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "roll_number", nullable = false)
    private int rollNumber;

    @Column(name = "enrollment_date", nullable = false)
    private LocalDate enrollmentDate;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "promoted_from_enrollment_id")
    private Long promotedFromEnrollmentId;

    @Column(length = 500)
    private String remarks;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Enrollment() {}

    public Enrollment(
            Long studentId,
            Long academicSessionId,
            Long campusId,
            Long departmentId,
            Long classId,
            Long sectionId,
            int rollNumber,
            LocalDate enrollmentDate,
            Long promotedFromEnrollmentId) {
        this.studentId = studentId;
        this.academicSessionId = academicSessionId;
        this.campusId = campusId;
        this.departmentId = departmentId;
        this.classId = classId;
        this.sectionId = sectionId;
        this.rollNumber = rollNumber;
        this.enrollmentDate = enrollmentDate != null ? enrollmentDate : LocalDate.now();
        this.promotedFromEnrollmentId = promotedFromEnrollmentId;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getAcademicSessionId() { return academicSessionId; }
    public Long getCampusId() { return campusId; }
    public Long getDepartmentId() { return departmentId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; this.updatedAt = Instant.now(); }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; this.updatedAt = Instant.now(); }
    public int getRollNumber() { return rollNumber; }
    public void setRollNumber(int rollNumber) { this.rollNumber = rollNumber; this.updatedAt = Instant.now(); }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public Long getPromotedFromEnrollmentId() { return promotedFromEnrollmentId; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; this.updatedAt = Instant.now(); }
    public Instant getDeletedAt() { return deletedAt; }
}

