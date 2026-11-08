package com.schoolerp.attendance.application;

import com.schoolerp.attendance.domain.AbsenceAlertQueue;
import com.schoolerp.attendance.domain.AttendanceSession;
import com.schoolerp.attendance.domain.LeaveApplication;
import com.schoolerp.attendance.domain.StaffAttendanceRecord;
import com.schoolerp.attendance.domain.StudentAttendanceRecord;
import com.schoolerp.attendance.infrastructure.AbsenceAlertQueueRepository;
import com.schoolerp.attendance.infrastructure.AttendanceSessionRepository;
import com.schoolerp.attendance.infrastructure.LeaveApplicationRepository;
import com.schoolerp.attendance.infrastructure.StaffAttendanceRecordRepository;
import com.schoolerp.attendance.infrastructure.StudentAttendanceRecordRepository;
import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.messaging.EventPriority;
import com.schoolerp.shared.messaging.OutboxEventWriter;
import com.schoolerp.staff.application.StaffService;
import com.schoolerp.staff.domain.Staff;
import com.schoolerp.students.application.StudentService;
import com.schoolerp.students.domain.Enrollment;
import com.schoolerp.students.domain.Guardian;
import com.schoolerp.students.domain.Student;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final StudentAttendanceRecordRepository studentRecordRepository;
    private final StaffAttendanceRecordRepository staffRecordRepository;
    private final LeaveApplicationRepository leaveRepository;
    private final AbsenceAlertQueueRepository alertQueueRepository;
    private final StudentService studentService;
    private final StaffService staffService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final OutboxEventWriter outboxEventWriter;

    public AttendanceService(
            AttendanceSessionRepository sessionRepository,
            StudentAttendanceRecordRepository studentRecordRepository,
            StaffAttendanceRecordRepository staffRecordRepository,
            LeaveApplicationRepository leaveRepository,
            AbsenceAlertQueueRepository alertQueueRepository,
            StudentService studentService,
            StaffService staffService,
            SettingsService settingsService,
            AuditService auditService,
            OutboxEventWriter outboxEventWriter) {
        this.sessionRepository = sessionRepository;
        this.studentRecordRepository = studentRecordRepository;
        this.staffRecordRepository = staffRecordRepository;
        this.leaveRepository = leaveRepository;
        this.alertQueueRepository = alertQueueRepository;
        this.studentService = studentService;
        this.staffService = staffService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.outboxEventWriter = outboxEventWriter;
    }

    public record StudentAttendanceRowDto(
            Long enrollmentId,
            Long studentId,
            String studentCode,
            String studentName,
            int rollNumber,
            String status,
            String remarks,
            Long leaveApplicationId) {}

    public record StudentAttendanceSheetDto(
            Long attendanceSessionId,
            Long academicSessionId,
            Long classId,
            Long sectionId,
            LocalDate attendanceDate,
            String sessionStatus,
            boolean editable,
            List<StudentAttendanceRowDto> rows) {}

    public record StudentMarkInput(
            Long enrollmentId,
            Long studentId,
            String status,
            String remarks) {}

    public record StaffAttendanceRowDto(
            Long staffId,
            String employeeCode,
            String fullName,
            String staffType,
            String status,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            String remarks) {}

    @Transactional(readOnly = true)
    public StudentAttendanceSheetDto getStudentSheet(
            Long academicSessionId,
            Long classId,
            Long sectionId,
            LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<Enrollment> enrollments = studentService.listSessionEnrollments(academicSessionId, classId, sectionId);
        Optional<AttendanceSession> existingSession = sessionRepository
                .findFirstByAcademicSessionIdAndSectionIdAndAttendanceDateAndDeletedAtIsNull(
                        academicSessionId, sectionId, targetDate);

        Map<Long, StudentAttendanceRecord> existingRecords = new HashMap<>();
        if (existingSession.isPresent()) {
            for (StudentAttendanceRecord r : studentRecordRepository.findAllByAttendanceSessionId(existingSession.get().getId())) {
                existingRecords.put(r.getEnrollmentId(), r);
            }
        }

        String defaultMark = settingsService.getString("attendance.default_mark", "PRESENT");
        List<StudentAttendanceRowDto> rows = new ArrayList<>();
        for (Enrollment e : enrollments) {
            Student student = studentService.getStudentOrThrow(e.getStudentId());
            StudentAttendanceRecord rec = existingRecords.get(e.getId());
            if (rec != null) {
                rows.add(new StudentAttendanceRowDto(
                        e.getId(), student.getId(), student.getStudentCode(), student.getFullName(),
                        e.getRollNumber(), rec.getStatus(), rec.getRemarks(), rec.getLeaveApplicationId()));
            } else {
                List<LeaveApplication> approvedLeaves = leaveRepository
                        .findAllByStudentEnrollmentIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndDeletedAtIsNull(
                                e.getId(), "APPROVED", targetDate, targetDate);
                if (!approvedLeaves.isEmpty()) {
                    rows.add(new StudentAttendanceRowDto(
                            e.getId(), student.getId(), student.getStudentCode(), student.getFullName(),
                            e.getRollNumber(), "EXCUSED", "Approved leave", approvedLeaves.get(0).getId()));
                } else {
                    rows.add(new StudentAttendanceRowDto(
                            e.getId(), student.getId(), student.getStudentCode(), student.getFullName(),
                            e.getRollNumber(), defaultMark, "", null));
                }
            }
        }

        boolean editable = true;
        String sessionStatus = "DRAFT";
        Long attendanceSessionId = null;
        if (existingSession.isPresent()) {
            AttendanceSession s = existingSession.get();
            attendanceSessionId = s.getId();
            sessionStatus = s.getStatus();
            editable = isEditable(s);
        }

        Long resolvedClassId = classId != null ? classId : (enrollments.isEmpty() ? 1L : enrollments.get(0).getClassId());
        return new StudentAttendanceSheetDto(
                attendanceSessionId, academicSessionId, resolvedClassId, sectionId, targetDate, sessionStatus, editable, rows);
    }

    @Transactional
    public AttendanceSession submitStudentAttendance(
            Long academicSessionId,
            Long campusId,
            Long classId,
            Long sectionId,
            LocalDate attendanceDate,
            List<StudentMarkInput> marks,
            String editReason,
            Long actorUserId) {
        Optional<AttendanceSession> existingOpt = sessionRepository
                .findFirstByAcademicSessionIdAndSectionIdAndAttendanceDateAndDeletedAtIsNull(
                        academicSessionId, sectionId, attendanceDate);

        if (existingOpt.isPresent()) {
            return amendStudentAttendance(existingOpt.get().getId(), marks, editReason, actorUserId);
        }

        String mode = settingsService.getString("attendance.mode", "DAILY_SECTION");
        AttendanceSession session = new AttendanceSession(
                academicSessionId, campusId != null ? campusId : 1L, classId, sectionId, null, null,
                attendanceDate, mode, actorUserId);
        session = sessionRepository.save(session);

        int delayMinutes = settingsService.getInt("attendance.notify_delay_minutes", 30);
        int present = 0, absent = 0, late = 0, excused = 0;

        for (StudentMarkInput mark : marks) {
            String status = mark.status() != null ? mark.status().trim().toUpperCase() : "PRESENT";
            switch (status) {
                case "PRESENT", "HALF_DAY" -> present++;
                case "ABSENT" -> absent++;
                case "LATE" -> late++;
                case "EXCUSED" -> excused++;
                default -> present++;
            }
            StudentAttendanceRecord record = new StudentAttendanceRecord(
                    session.getId(), mark.enrollmentId(), mark.studentId(), status, "MANUAL_WEB", null, mark.remarks(), actorUserId);
            record = studentRecordRepository.save(record);

            if ("ABSENT".equals(status)) {
                List<Guardian> guardians = studentService.listStudentGuardians(mark.studentId());
                Long primaryGuardianId = guardians.isEmpty() ? null : guardians.get(0).getId();
                alertQueueRepository.save(new AbsenceAlertQueue(
                        record.getId(),
                        mark.studentId(),
                        primaryGuardianId,
                        attendanceDate,
                        Instant.now().plus(Duration.ofMinutes(delayMinutes))));
            }
        }

        session.updateCounts(marks.size(), present, absent, late, excused);
        session = sessionRepository.save(session);

        auditService.record("ATTENDANCE_SESSION_SUBMITTED", "AttendanceSession", String.valueOf(session.getId()), null,
                Map.of("sectionId", sectionId, "attendanceDate", attendanceDate.toString(),
                        "presentCount", present, "absentCount", absent, "lateCount", late));

        outboxEventWriter.write("AttendanceSession", String.valueOf(session.getId()),
                "StudentAttendanceSubmittedEvent", "attendance.session_submitted", EventPriority.NORMAL,
                Map.of("attendanceSessionId", session.getId(), "sectionId", sectionId,
                        "attendanceDate", attendanceDate.toString(), "presentCount", present, "absentCount", absent));

        return session;
    }

    @Transactional
    public AttendanceSession amendStudentAttendance(
            Long sessionId,
            List<StudentMarkInput> marks,
            String editReason,
            Long actorUserId) {
        AttendanceSession session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attendance session not found"));
        if (!isEditable(session)) {
            throw new ApiException(HttpStatus.CONFLICT, "Attendance edit window has expired; admin unlock required");
        }
        if (editReason == null || editReason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Edit reason is required when modifying submitted attendance");
        }

        int delayMinutes = settingsService.getInt("attendance.notify_delay_minutes", 30);
        int present = 0, absent = 0, late = 0, excused = 0;

        for (StudentMarkInput mark : marks) {
            String newStatus = mark.status() != null ? mark.status().trim().toUpperCase() : "PRESENT";
            switch (newStatus) {
                case "PRESENT", "HALF_DAY" -> present++;
                case "ABSENT" -> absent++;
                case "LATE" -> late++;
                case "EXCUSED" -> excused++;
                default -> present++;
            }

            Optional<StudentAttendanceRecord> existingOpt = studentRecordRepository
                    .findByAttendanceSessionIdAndEnrollmentId(session.getId(), mark.enrollmentId());
            if (existingOpt.isPresent()) {
                StudentAttendanceRecord record = existingOpt.get();
                String oldStatus = record.getStatus();
                if (!oldStatus.equals(newStatus)) {
                    record.updateStatus(newStatus, mark.remarks(), actorUserId);
                    studentRecordRepository.save(record);

                    auditService.record("ATTENDANCE_RECORD_AMENDED", "StudentAttendanceRecord",
                            String.valueOf(record.getId()),
                            Map.of("status", oldStatus, "studentId", record.getStudentId()),
                            Map.of("status", newStatus, "reason", editReason.trim()));

                    // Suppress pending absence alert if corrected from ABSENT to PRESENT/LATE/EXCUSED (BR-07)
                    if ("ABSENT".equals(oldStatus) && !"ABSENT".equals(newStatus)) {
                        alertQueueRepository.findByStudentAttendanceRecordId(record.getId())
                                .ifPresent(alert -> {
                                    alert.suppress();
                                    alertQueueRepository.save(alert);
                                });
                    } else if ("ABSENT".equals(newStatus)) {
                        List<Guardian> guardians = studentService.listStudentGuardians(mark.studentId());
                        Long primaryGuardianId = guardians.isEmpty() ? null : guardians.get(0).getId();
                        AbsenceAlertQueue alert = alertQueueRepository.findByStudentAttendanceRecordId(record.getId())
                                .orElseGet(() -> new AbsenceAlertQueue(
                                        record.getId(), mark.studentId(), primaryGuardianId,
                                        session.getAttendanceDate(), Instant.now().plus(Duration.ofMinutes(delayMinutes))));
                        alert.markPending(Instant.now().plus(Duration.ofMinutes(delayMinutes)));
                        alertQueueRepository.save(alert);
                    }
                }
            } else {
                StudentAttendanceRecord created = new StudentAttendanceRecord(
                        session.getId(), mark.enrollmentId(), mark.studentId(), newStatus, "MANUAL_WEB", null, mark.remarks(), actorUserId);
                studentRecordRepository.save(created);
            }
        }

        session.updateCounts(marks.size(), present, absent, late, excused);
        return sessionRepository.save(session);
    }

    @Transactional
    public AttendanceSession unlockAttendanceSession(Long sessionId, String overrideReason, Long actorUserId) {
        AttendanceSession session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attendance session not found"));
        session.resetSubmittedAt();
        session = sessionRepository.save(session);
        auditService.record("ATTENDANCE_SESSION_ADMIN_UNLOCKED", "AttendanceSession", String.valueOf(sessionId), null,
                Map.of("overrideReason", overrideReason, "unlockedBy", actorUserId != null ? actorUserId : 1L));
        return session;
    }

    @Transactional(readOnly = true)
    public List<StaffAttendanceRowDto> getStaffSheet(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<Staff> staffList = staffService.listStaff(PageRequest.of(0, 100)).getContent();
        Map<Long, StaffAttendanceRecord> existing = new HashMap<>();
        for (StaffAttendanceRecord r : staffRecordRepository.findAllByAttendanceDate(targetDate)) {
            existing.put(r.getStaffId(), r);
        }

        List<StaffAttendanceRowDto> rows = new ArrayList<>();
        for (Staff s : staffList) {
            StaffAttendanceRecord rec = existing.get(s.getId());
            if (rec != null) {
                rows.add(new StaffAttendanceRowDto(
                        s.getId(), s.getEmployeeCode(), s.getFullName(), s.getStaffType(),
                        rec.getStatus(), rec.getCheckInTime(), rec.getCheckOutTime(), rec.getRemarks()));
            } else {
                rows.add(new StaffAttendanceRowDto(
                        s.getId(), s.getEmployeeCode(), s.getFullName(), s.getStaffType(),
                        "PRESENT", LocalTime.of(8, 30), LocalTime.of(15, 30), ""));
            }
        }
        return rows;
    }

    @Transactional
    public List<StaffAttendanceRowDto> submitStaffSheet(LocalDate date, List<StaffAttendanceRowDto> rows, Long actorUserId) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        for (StaffAttendanceRowDto row : rows) {
            Optional<StaffAttendanceRecord> existingOpt = staffRecordRepository
                    .findByStaffIdAndAttendanceDate(row.staffId(), targetDate);
            if (existingOpt.isPresent()) {
                StaffAttendanceRecord rec = existingOpt.get();
                rec.update(row.status(), row.checkInTime(), row.checkOutTime(), row.remarks(), actorUserId);
                staffRecordRepository.save(rec);
            } else {
                staffRecordRepository.save(new StaffAttendanceRecord(
                        row.staffId(), targetDate, row.status(), row.checkInTime(), row.checkOutTime(), row.remarks(), actorUserId));
            }
        }
        auditService.record("STAFF_ATTENDANCE_SUBMITTED", "StaffAttendance", targetDate.toString(), null,
                Map.of("date", targetDate.toString(), "count", rows.size()));
        return getStaffSheet(targetDate);
    }

    @Transactional
    public LeaveApplication submitLeave(
            String applicantType,
            Long studentEnrollmentId,
            Long staffId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Long requestedByUserId) {
        if (endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        LeaveApplication leave = new LeaveApplication(
                applicantType, studentEnrollmentId, staffId, leaveType, startDate, endDate, reason, requestedByUserId);
        LeaveApplication saved = leaveRepository.save(leave);
        auditService.record("LEAVE_APPLICATION_SUBMITTED", "LeaveApplication", String.valueOf(saved.getId()), null,
                Map.of("applicantType", saved.getApplicantType(), "startDate", startDate.toString(), "endDate", endDate.toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<LeaveApplication> listLeaves(Pageable pageable) {
        return leaveRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional
    public LeaveApplication reviewLeave(Long id, String status, String reviewRemarks, Long reviewerUserId) {
        LeaveApplication leave = leaveRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave application not found"));
        String normalized = status.trim().toUpperCase();
        if (!"APPROVED".equals(normalized) && !"REJECTED".equals(normalized) && !"CANCELLED".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid leave status: " + status);
        }
        leave.review(normalized, reviewRemarks, reviewerUserId);
        LeaveApplication saved = leaveRepository.save(leave);

        // BR-06: Approved student leave auto-marks overlapping existing attendance records as EXCUSED
        if ("APPROVED".equals(normalized) && saved.getStudentEnrollmentId() != null) {
            List<StudentAttendanceRecord> records = studentRecordRepository.findAllByEnrollmentId(saved.getStudentEnrollmentId());
            for (StudentAttendanceRecord r : records) {
                sessionRepository.findByIdAndDeletedAtIsNull(r.getAttendanceSessionId()).ifPresent(sess -> {
                    LocalDate d = sess.getAttendanceDate();
                    if (!d.isBefore(saved.getStartDate()) && !d.isAfter(saved.getEndDate())) {
                        r.markExcusedByLeave(saved.getId(), reviewerUserId);
                        studentRecordRepository.save(r);
                    }
                });
            }
        }

        auditService.record("LEAVE_APPLICATION_REVIEWED", "LeaveApplication", String.valueOf(saved.getId()), null,
                Map.of("status", normalized, "remarks", reviewRemarks != null ? reviewRemarks : ""));
        return saved;
    }

    private boolean isEditable(AttendanceSession session) {
        if ("LOCKED".equals(session.getStatus())) {
            return false;
        }
        int windowHours = settingsService.getInt("attendance.edit_window_hours", 48);
        return Instant.now().isBefore(session.getSubmittedAt().plus(Duration.ofHours(windowHours)));
    }
}

