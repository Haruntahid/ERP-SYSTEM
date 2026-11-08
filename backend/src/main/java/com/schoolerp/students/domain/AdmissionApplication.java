package com.schoolerp.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "admission_applications")
public class AdmissionApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_no", nullable = false, length = 40, unique = true)
    private String applicationNo;

    @Column(name = "academic_session_id", nullable = false)
    private Long academicSessionId;

    @Column(name = "target_campus_id", nullable = false)
    private Long targetCampusId;

    @Column(name = "target_class_id", nullable = false)
    private Long targetClassId;

    @Column(name = "target_section_id")
    private Long targetSectionId;

    @Column(name = "applicant_first_name", nullable = false, length = 100)
    private String applicantFirstName;

    @Column(name = "applicant_last_name", nullable = false, length = 100)
    private String applicantLastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    private String gender;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(name = "previous_school_name", length = 200)
    private String previousSchoolName;

    @Column(name = "previous_class_result", length = 100)
    private String previousClassResult;

    @Column(name = "guardian_name", nullable = false, length = 200)
    private String guardianName;

    @Column(name = "guardian_phone", nullable = false, length = 30)
    private String guardianPhone;

    @Column(name = "guardian_email", length = 190)
    private String guardianEmail;

    @Column(name = "guardian_relationship", nullable = false, length = 30)
    private String guardianRelationship = "FATHER";

    @Column(name = "application_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal applicationFeeAmount = BigDecimal.ZERO;

    @Column(name = "application_fee_currency", nullable = false, length = 3)
    private String applicationFeeCurrency = "BDT";

    @Column(nullable = false, length = 25)
    private String status = "SUBMITTED";

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "enrolled_student_id")
    private Long enrolledStudentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AdmissionApplication() {}

    public AdmissionApplication(
            String applicationNo,
            Long academicSessionId,
            Long targetCampusId,
            Long targetClassId,
            Long targetSectionId,
            String applicantFirstName,
            String applicantLastName,
            LocalDate dateOfBirth,
            String gender,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelationship,
            String previousSchoolName) {
        this.applicationNo = applicationNo;
        this.academicSessionId = academicSessionId;
        this.targetCampusId = targetCampusId;
        this.targetClassId = targetClassId;
        this.targetSectionId = targetSectionId;
        this.applicantFirstName = applicantFirstName;
        this.applicantLastName = applicantLastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.guardianEmail = guardianEmail;
        this.guardianRelationship = guardianRelationship != null && !guardianRelationship.isBlank() ? guardianRelationship : "FATHER";
        this.previousSchoolName = previousSchoolName;
        this.status = "SUBMITTED";
    }

    public Long getId() { return id; }
    public String getApplicationNo() { return applicationNo; }
    public Long getAcademicSessionId() { return academicSessionId; }
    public Long getTargetCampusId() { return targetCampusId; }
    public Long getTargetClassId() { return targetClassId; }
    public Long getTargetSectionId() { return targetSectionId; }
    public String getApplicantFirstName() { return applicantFirstName; }
    public String getApplicantLastName() { return applicantLastName; }
    public String getApplicantFullName() { return (applicantFirstName + " " + applicantLastName).trim(); }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String getBloodGroup() { return bloodGroup; }
    public String getPreviousSchoolName() { return previousSchoolName; }
    public String getGuardianName() { return guardianName; }
    public String getGuardianPhone() { return guardianPhone; }
    public String getGuardianEmail() { return guardianEmail; }
    public String getGuardianRelationship() { return guardianRelationship; }
    public String getStatus() { return status; }
    public void setStatus(String status, Long reviewerUserId, String reviewNotes) {
        this.status = status;
        this.reviewerUserId = reviewerUserId;
        this.reviewNotes = reviewNotes;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    public String getReviewNotes() { return reviewNotes; }
    public Long getEnrolledStudentId() { return enrolledStudentId; }
    public void markEnrolled(Long studentId) {
        this.status = "ENROLLED";
        this.enrolledStudentId = studentId;
        this.updatedAt = Instant.now();
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}

