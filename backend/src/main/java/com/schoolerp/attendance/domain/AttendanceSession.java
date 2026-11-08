package com.schoolerp.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_session_id", nullable = false)
    private Long academicSessionId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "period_number")
    private Integer periodNumber;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false, length = 20)
    private String mode = "DAILY_SECTION";

    @Column(nullable = false, length = 20)
    private String status = "SUBMITTED";

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "total_students", nullable = false)
    private int totalStudents;

    @Column(name = "present_count", nullable = false)
    private int presentCount;

    @Column(name = "absent_count", nullable = false)
    private int absentCount;

    @Column(name = "late_count", nullable = false)
    private int lateCount;

    @Column(name = "excused_count", nullable = false)
    private int excusedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AttendanceSession() {}

    public AttendanceSession(
            Long academicSessionId,
            Long campusId,
            Long classId,
            Long sectionId,
            Long subjectId,
            Integer periodNumber,
            LocalDate attendanceDate,
            String mode,
            Long submittedByUserId) {
        this.academicSessionId = academicSessionId;
        this.campusId = campusId != null ? campusId : 1L;
        this.classId = classId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.periodNumber = periodNumber;
        this.attendanceDate = attendanceDate;
        this.mode = mode != null && !mode.isBlank() ? mode : "DAILY_SECTION";
        this.submittedByUserId = submittedByUserId != null ? submittedByUserId : 1L;
        this.status = "SUBMITTED";
    }

    public Long getId() { return id; }
    public Long getAcademicSessionId() { return academicSessionId; }
    public Long getCampusId() { return campusId; }
    public Long getClassId() { return classId; }
    public Long getSectionId() { return sectionId; }
    public Long getSubjectId() { return subjectId; }
    public Integer getPeriodNumber() { return periodNumber; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public String getMode() { return mode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void resetSubmittedAt() { this.submittedAt = Instant.now(); this.lockedAt = null; this.status = "SUBMITTED"; this.updatedAt = Instant.now(); }
    public Instant getLockedAt() { return lockedAt; }
    public void lockNow() { this.status = "LOCKED"; this.lockedAt = Instant.now(); this.updatedAt = Instant.now(); }
    public int getTotalStudents() { return totalStudents; }
    public int getPresentCount() { return presentCount; }
    public int getAbsentCount() { return absentCount; }
    public int getLateCount() { return lateCount; }
    public int getExcusedCount() { return excusedCount; }
    public void updateCounts(int total, int present, int absent, int late, int excused) {
        this.totalStudents = total;
        this.presentCount = present;
        this.absentCount = absent;
        this.lateCount = late;
        this.excusedCount = excused;
        this.updatedAt = Instant.now();
    }
}

