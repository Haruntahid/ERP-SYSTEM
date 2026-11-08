package com.schoolerp.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "staff_attendance_records")
public class StaffAttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(nullable = false, length = 20)
    private String source = "MANUAL_WEB";

    @Column(name = "leave_application_id")
    private Long leaveApplicationId;

    @Column(length = 300)
    private String remarks;

    @Column(name = "recorded_by_user_id", nullable = false)
    private Long recordedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StaffAttendanceRecord() {}

    public StaffAttendanceRecord(
            Long staffId,
            LocalDate attendanceDate,
            String status,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            String remarks,
            Long recordedByUserId) {
        this.staffId = staffId;
        this.attendanceDate = attendanceDate;
        this.status = status.toUpperCase();
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.remarks = remarks;
        this.recordedByUserId = recordedByUserId != null ? recordedByUserId : 1L;
    }

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public String getStatus() { return status; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public String getRemarks() { return remarks; }
    public void update(String status, LocalTime checkIn, LocalTime checkOut, String remarks, Long userId) {
        this.status = status.toUpperCase();
        this.checkInTime = checkIn;
        this.checkOutTime = checkOut;
        this.remarks = remarks;
        this.recordedByUserId = userId != null ? userId : 1L;
        this.updatedAt = Instant.now();
    }
}

