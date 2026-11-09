package com.schoolerp.staff.api;

import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.RequiresFeature;
import com.schoolerp.staff.application.StaffService;
import com.schoolerp.staff.domain.Designation;
import com.schoolerp.staff.domain.Staff;
import com.schoolerp.staff.domain.TeacherSubjectAssignment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/staff")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Staff & Teachers", description = "Staff directory and teacher assignments")
@RequiresFeature("staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    public record SaveDesignationRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String title,
            @Size(max = 20) String category) {}

    public record CreateStaffRequest(@NotNull Long campusId, Long departmentId, @NotNull Long designationId,
            @Size(max = 50) String employeeCode, @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName, @Size(max = 190) String email,
            @NotBlank @Size(max = 40) String phone, @Size(max = 20) String staffType,
            @NotNull LocalDate joinDate) {}

    public record ChangeStaffStatusRequest(@NotBlank String status) {}

    public record CreateTeacherAssignmentRequest(@NotNull Long academicSessionId, @NotNull Long staffId,
            @NotNull Long sectionId, Long subjectId, boolean classTeacher, @Size(max = 20) String roleType) {}

    @GetMapping("/designations")
    @PreAuthorize("@perm.has(authentication, 'staff.read')")
    @Operation(summary = "List designations")
    public PageResponse<Designation> listDesignations(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Designation> result = staffService.listDesignations(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/designations")
    @PreAuthorize("@perm.has(authentication, 'staff.write')")
    @Operation(summary = "Create or update a designation")
    public Designation saveDesignation(@Valid @RequestBody SaveDesignationRequest request) {
        return staffService.saveDesignation(request.code(), request.title(), request.category());
    }

    @DeleteMapping("/designations/{id}")
    @PreAuthorize("@perm.has(authentication, 'staff.write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a designation")
    public void deleteDesignation(@PathVariable Long id) {
        staffService.deleteDesignation(id);
    }

    @GetMapping
    @PreAuthorize("@perm.has(authentication, 'staff.read')")
    @Operation(summary = "List staff")
    public PageResponse<Staff> listStaff(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Staff> result = staffService.listStaff(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    @PreAuthorize("@perm.has(authentication, 'staff.write')")
    @Operation(summary = "Create a staff record")
    @ResponseStatus(HttpStatus.CREATED)
    public Staff createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return staffService.createStaff(
                request.campusId(), request.departmentId(), request.designationId(), request.employeeCode(),
                request.firstName(), request.lastName(), request.email(), request.phone(), request.staffType(), request.joinDate());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has(authentication, 'staff.write')")
    @Operation(summary = "Update a staff record")
    public Staff updateStaff(@PathVariable Long id, @Valid @RequestBody CreateStaffRequest request) {
        return staffService.updateStaff(
                id, request.designationId(), request.firstName(), request.lastName(),
                request.email(), request.phone(), request.staffType(), request.joinDate());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has(authentication, 'staff.write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a staff record")
    public void deleteStaff(@PathVariable Long id) {
        staffService.deleteStaff(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@perm.has(authentication, 'staff.status.manage')")
    @Operation(summary = "Change staff employment status")
    public Staff changeStaffStatus(@PathVariable Long id, @Valid @RequestBody ChangeStaffStatusRequest request) {
        return staffService.changeStatus(id, request.status());
    }

    @GetMapping("/assignments")
    @PreAuthorize("@perm.has(authentication, 'staff.read')")
    @Operation(summary = "List teacher assignments")
    public List<TeacherSubjectAssignment> listAssignments() {
        return staffService.listAssignments();
    }

    @PostMapping("/assignments")
    @PreAuthorize("@perm.has(authentication, 'staff.assign')")
    @Operation(summary = "Assign a teacher to a section and subject")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherSubjectAssignment createAssignment(@Valid @RequestBody CreateTeacherAssignmentRequest request) {
        return staffService.assignTeacher(request.academicSessionId(), request.staffId(), request.sectionId(), request.subjectId(),
                request.classTeacher(), request.roleType());
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize("@perm.has(authentication, 'staff.assign')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a teacher assignment")
    public void deleteAssignment(@PathVariable Long assignmentId) {
        staffService.revokeAssignment(assignmentId);
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}

