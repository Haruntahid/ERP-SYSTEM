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
@Table(name = "absence_alert_queue")
public class AbsenceAlertQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_attendance_record_id", nullable = false, unique = true)
    private Long studentAttendanceRecordId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "guardian_id")
    private Long guardianId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "scheduled_send_at", nullable = false)
    private Instant scheduledSendAt;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AbsenceAlertQueue() {}

    public AbsenceAlertQueue(
            Long studentAttendanceRecordId,
            Long studentId,
            Long guardianId,
            LocalDate attendanceDate,
            Instant scheduledSendAt) {
        this.studentAttendanceRecordId = studentAttendanceRecordId;
        this.studentId = studentId;
        this.guardianId = guardianId;
        this.attendanceDate = attendanceDate;
        this.scheduledSendAt = scheduledSendAt;
    }

    public Long getId() { return id; }
    public Long getStudentAttendanceRecordId() { return studentAttendanceRecordId; }
    public Long getStudentId() { return studentId; }
    public Long getGuardianId() { return guardianId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public Instant getScheduledSendAt() { return scheduledSendAt; }
    public String getStatus() { return status; }
    public void suppress() {
        this.status = "SUPPRESSED";
        this.processedAt = Instant.now();
    }
    public void markPending(Instant newScheduledSendAt) {
        this.status = "PENDING";
        this.scheduledSendAt = newScheduledSendAt;
        this.processedAt = null;
    }
}

