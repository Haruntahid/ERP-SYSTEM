package com.schoolerp.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "student_attendance_records")
public class StudentAttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_session_id", nullable = false)
    private Long attendanceSessionId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String source = "MANUAL_WEB";

    @Column(name = "leave_application_id")
    private Long leaveApplicationId;

    @Column(length = 300)
    private String remarks;

    @Column(name = "last_modified_by_user_id", nullable = false)
    private Long lastModifiedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentAttendanceRecord() {}

    public StudentAttendanceRecord(
            Long attendanceSessionId,
            Long enrollmentId,
            Long studentId,
            String status,
            String source,
            Long leaveApplicationId,
            String remarks,
            Long lastModifiedByUserId) {
        this.attendanceSessionId = attendanceSessionId;
        this.enrollmentId = enrollmentId;
        this.studentId = studentId;
        this.status = status.toUpperCase();
        this.source = source != null ? source : "MANUAL_WEB";
        this.leaveApplicationId = leaveApplicationId;
        this.remarks = remarks;
        this.lastModifiedByUserId = lastModifiedByUserId != null ? lastModifiedByUserId : 1L;
    }

    public Long getId() { return id; }
    public Long getAttendanceSessionId() { return attendanceSessionId; }
    public Long getEnrollmentId() { return enrollmentId; }
    public Long getStudentId() { return studentId; }
    public String getStatus() { return status; }
    public void updateStatus(String status, String remarks, Long modifiedByUserId) {
        this.status = status.toUpperCase();
        this.remarks = remarks;
        this.lastModifiedByUserId = modifiedByUserId != null ? modifiedByUserId : 1L;
        this.updatedAt = Instant.now();
    }
    public void markExcusedByLeave(Long leaveId, Long modifiedByUserId) {
        this.status = "EXCUSED";
        this.source = "LEAVE_AUTO";
        this.leaveApplicationId = leaveId;
        this.lastModifiedByUserId = modifiedByUserId != null ? modifiedByUserId : 1L;
        this.updatedAt = Instant.now();
    }
    public String getSource() { return source; }
    public Long getLeaveApplicationId() { return leaveApplicationId; }
    public String getRemarks() { return remarks; }
    public Long getLastModifiedByUserId() { return lastModifiedByUserId; }
}

