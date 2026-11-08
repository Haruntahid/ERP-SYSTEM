package com.schoolerp.attendance.api;

import com.schoolerp.attendance.application.AttendanceService;
import com.schoolerp.attendance.application.AttendanceService.StaffAttendanceRowDto;
import com.schoolerp.attendance.application.AttendanceService.StudentAttendanceSheetDto;
import com.schoolerp.attendance.application.AttendanceService.StudentMarkInput;
import com.schoolerp.attendance.domain.AttendanceSession;
import com.schoolerp.attendance.domain.LeaveApplication;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.RequiresFeature;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/attendance")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Attendance & Leave", description = "Student & staff attendance, leave applications, and absence alerts")
@RequiresFeature("attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public record SubmitStudentAttendanceRequest(
            @NotNull Long academicSessionId,
            Long campusId,
            @NotNull Long classId,
            @NotNull Long sectionId,
            @NotNull LocalDate attendanceDate,
            @NotEmpty List<StudentMarkInput> marks,
            @Size(max = 300) String editReason) {}

    public record AmendStudentAttendanceRequest(
            @NotEmpty List<StudentMarkInput> marks,
            @NotBlank @Size(max = 300) String editReason) {}

    public record AdminUnlockAttendanceRequest(
            @NotBlank @Size(max = 300) String overrideReason) {}

    public record SubmitStaffAttendanceRequest(
            @NotNull LocalDate attendanceDate,
            @NotEmpty List<StaffAttendanceRowDto> rows) {}

    public record CreateLeaveApplicationRequest(
            @NotBlank String applicantType,
            Long studentEnrollmentId,
            Long staffId,
            @NotBlank String leaveType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 1000) String reason) {}

    public record ReviewLeaveApplicationRequest(
            @NotBlank String status,
            @Size(max = 500) String reviewRemarks) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    @GetMapping("/students/sheet")
    @PreAuthorize("@perm.has(authentication, 'attendance.read')")
    @Operation(summary = "Get student attendance roll-call sheet for a section and date")
    public StudentAttendanceSheetDto getStudentSheet(
            @RequestParam Long sessionId,
            @RequestParam(required = false) Long classId,
            @RequestParam Long sectionId,
            @RequestParam(required = false) LocalDate date) {
        return attendanceService.getStudentSheet(sessionId, classId, sectionId, date);
    }

    @PostMapping("/students/sessions")
    @PreAuthorize("@perm.has(authentication, 'attendance.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit or update student attendance for a section and date")
    public AttendanceSession submitStudentAttendance(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody SubmitStudentAttendanceRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return attendanceService.submitStudentAttendance(
                request.academicSessionId(),
                request.campusId(),
                request.classId(),
                request.sectionId(),
                request.attendanceDate(),
                request.marks(),
                request.editReason(),
                userId);
    }

    @PutMapping("/students/sessions/{sessionId}")
    @PreAuthorize("@perm.has(authentication, 'attendance.write')")
    @Operation(summary = "Amend an already submitted student attendance session with mandatory audit reason")
    public AttendanceSession amendStudentAttendance(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody AmendStudentAttendanceRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return attendanceService.amendStudentAttendance(sessionId, request.marks(), request.editReason(), userId);
    }

    @PostMapping("/students/sessions/{sessionId}/unlock")
    @PreAuthorize("@perm.has(authentication, 'attendance.override_lock')")
    @Operation(summary = "Admin unlock of a locked attendance session")
    public AttendanceSession unlockAttendanceSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody AdminUnlockAttendanceRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return attendanceService.unlockAttendanceSession(sessionId, request.overrideReason(), userId);
    }

    @GetMapping("/staff/sheet")
    @PreAuthorize("@perm.has(authentication, 'attendance.staff.read')")
    @Operation(summary = "Get daily staff attendance sheet")
    public List<StaffAttendanceRowDto> getStaffSheet(@RequestParam(required = false) LocalDate date) {
        return attendanceService.getStaffSheet(date);
    }

    @PostMapping("/staff/sheet")
    @PreAuthorize("@perm.has(authentication, 'attendance.staff.write')")
    @Operation(summary = "Submit daily staff attendance sheet")
    public List<StaffAttendanceRowDto> submitStaffSheet(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody SubmitStaffAttendanceRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return attendanceService.submitStaffSheet(request.attendanceDate(), request.rows(), userId);
    }

    @PostMapping("/leaves")
    @PreAuthorize("@perm.has(authentication, 'leave.request')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a student or staff leave application")
    public LeaveApplication createLeave(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody CreateLeaveApplicationRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return attendanceService.submitLeave(
                request.applicantType(),
                request.studentEnrollmentId(),
                request.staffId(),
                request.leaveType(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                userId);
    }

    @GetMapping("/leaves")
    @PreAuthorize("@perm.has(authentication, 'leave.read')")
    @Operation(summary = "List leave applications")
    public PageResponse<LeaveApplication> listLeaves(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<LeaveApplication> result = attendanceService.listLeaves(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/leaves/{id}/review")
    @PreAuthorize("@perm.has(authentication, 'leave.approve')")
    @Operation(summary = "Approve or reject a leave application")
    public LeaveApplication reviewLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody ReviewLeaveApplicationRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return attendanceService.reviewLeave(id, request.status(), request.reviewRemarks(), userId);
    }
}

