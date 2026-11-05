package com.schoolerp.staff.application;

import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.staff.domain.Designation;
import com.schoolerp.staff.domain.Staff;
import com.schoolerp.staff.domain.TeacherSubjectAssignment;
import com.schoolerp.staff.infrastructure.DesignationRepository;
import com.schoolerp.staff.infrastructure.StaffRepository;
import com.schoolerp.staff.infrastructure.TeacherSubjectAssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final StaffRepository staffRepository;
    private final DesignationRepository designationRepository;
    private final TeacherSubjectAssignmentRepository assignmentRepository;
    private final AuditService auditService;

    public StaffService(
            StaffRepository staffRepository,
            DesignationRepository designationRepository,
            TeacherSubjectAssignmentRepository assignmentRepository,
            AuditService auditService) {
        this.staffRepository = staffRepository;
        this.designationRepository = designationRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Designation> listDesignations(Pageable pageable) {
        return designationRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional
    public Designation saveDesignation(String code, String title, String category) {
        String normalizedCode = code == null ? null : code.trim();
        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Designation code is required");
        }
        if (title == null || title.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Designation title is required");
        }

        return designationRepository.findByCodeAndDeletedAtIsNull(normalizedCode)
                .map(existing -> {
                    existing.setTitle(title.trim());
                    existing.setCategory(category == null || category.isBlank() ? "TEACHING" : category.trim().toUpperCase());
                    return designationRepository.save(existing);
                })
                .orElseGet(() -> designationRepository.save(new Designation(normalizedCode, title.trim(), category)));
    }

    @Transactional(readOnly = true)
    public Page<Staff> listStaff(Pageable pageable) {
        return staffRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Staff findByIdOrThrow(Long staffId) {
        return staffRepository.findByIdAndDeletedAtIsNull(staffId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Staff member not found"));
    }

    @Transactional
    public Staff createStaff(
            Long campusId,
            Long departmentId,
            Long designationId,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String phone,
            String staffType,
            LocalDate joinDate) {
        if (campusId == null || designationId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Campus and designation are required");
        }
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "First and last name are required");
        }
        if (employeeCode == null || employeeCode.isBlank()) {
            employeeCode = EmployeeCodeGenerator.generate("EMP-{YEAR}-{SEQ:4}", 1L);
        }
        if (staffRepository.existsByEmployeeCodeAndDeletedAtIsNull(employeeCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists");
        }

        Staff staff = new Staff(campusId, departmentId, designationId, employeeCode, firstName.trim(), lastName.trim(), email, phone, staffType, joinDate);
        Staff saved = staffRepository.save(staff);
        auditService.record("STAFF_CREATED", "Staff", String.valueOf(saved.getId()), null,
                Map.of("employeeCode", saved.getEmployeeCode(), "fullName", saved.getFullName(), "campusId", saved.getCampusId()));
        return saved;
    }

    @Transactional
    public Staff changeStatus(Long staffId, String status) {
        Staff staff = findByIdOrThrow(staffId);
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        String previous = staff.getEmploymentStatus();
        staff.setEmploymentStatus(normalized);
        staff = staffRepository.save(staff);
        auditService.record("STAFF_STATUS_CHANGED", "Staff", String.valueOf(staffId), Map.of("status", previous), Map.of("status", normalized));
        return staff;
    }

    @Transactional(readOnly = true)
    public List<TeacherSubjectAssignment> listAssignments() {
        return assignmentRepository.findAllByDeletedAtIsNull();
    }

    @Transactional
    public TeacherSubjectAssignment assignTeacher(Long academicSessionId, Long staffId, Long sectionId, Long subjectId, boolean classTeacher, String roleType) {
        Staff staff = findByIdOrThrow(staffId);
        if (!"TEACHING".equalsIgnoreCase(staff.getStaffType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only teaching staff can be assigned to a section");
        }
        if (!"ACTIVE".equalsIgnoreCase(staff.getEmploymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only active staff can be assigned to a section");
        }

        TeacherSubjectAssignment assignment = new TeacherSubjectAssignment(academicSessionId, staffId, sectionId, subjectId, classTeacher, roleType);
        TeacherSubjectAssignment saved = assignmentRepository.save(assignment);
        auditService.record("TEACHER_ASSIGNED", "TeacherSubjectAssignment", String.valueOf(saved.getId()), null,
                Map.of("academicSessionId", academicSessionId, "staffId", staffId, "sectionId", sectionId, "subjectId", subjectId));
        return saved;
    }

    @Transactional
    public void revokeAssignment(Long assignmentId) {
        TeacherSubjectAssignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Teacher assignment not found"));
        assignment.softDelete();
        assignmentRepository.save(assignment);
        auditService.record("TEACHER_UNASSIGNED", "TeacherSubjectAssignment", String.valueOf(assignmentId), null,
                Map.of("staffId", assignment.getStaffId(), "sectionId", assignment.getSectionId()));
    }
}

