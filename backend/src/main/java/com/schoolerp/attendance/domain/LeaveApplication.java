package com.schoolerp.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "leave_applications")
public class LeaveApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_type", nullable = false, length = 20)
    private String applicantType;

    @Column(name = "student_enrollment_id")
    private Long studentEnrollmentId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "leave_type", nullable = false, length = 30)
    private String leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "attachment_file_id")
    private Long attachmentFileId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected LeaveApplication() {}

    public LeaveApplication(
            String applicantType,
            Long studentEnrollmentId,
            Long staffId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Long requestedByUserId) {
        this.applicantType = applicantType.toUpperCase();
        this.studentEnrollmentId = studentEnrollmentId;
        this.staffId = staffId;
        this.leaveType = leaveType.toUpperCase();
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalDays = Math.max(1, (int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
        this.reason = reason;
        this.requestedByUserId = requestedByUserId != null ? requestedByUserId : 1L;
    }

    public Long getId() { return id; }
    public String getApplicantType() { return applicantType; }
    public Long getStudentEnrollmentId() { return studentEnrollmentId; }
    public Long getStaffId() { return staffId; }
    public String getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getTotalDays() { return totalDays; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getReviewRemarks() { return reviewRemarks; }
    public void review(String newStatus, String remarks, Long reviewerUserId) {
        this.status = newStatus.toUpperCase();
        this.reviewRemarks = remarks;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}

