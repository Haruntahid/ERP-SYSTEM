package com.schoolerp.students.api;

import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.RequiresFeature;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import com.schoolerp.students.application.StudentService;
import com.schoolerp.students.domain.AdmissionApplication;
import com.schoolerp.students.domain.Enrollment;
import com.schoolerp.students.domain.Guardian;
import com.schoolerp.students.domain.Student;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Students, Guardians & Admissions", description = "Admission workflow, student directory, guardians, and session enrollments")
@RequiresFeature("students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    public record CreateAdmissionApplicationRequest(
            @NotNull Long academicSessionId,
            Long targetCampusId,
            @NotNull Long targetClassId,
            Long targetSectionId,
            @NotBlank @Size(max = 100) String applicantFirstName,
            @NotBlank @Size(max = 100) String applicantLastName,
            @NotNull LocalDate dateOfBirth,
            @NotBlank @Size(max = 20) String gender,
            @NotBlank @Size(max = 200) String guardianName,
            @NotBlank @Size(max = 30) String guardianPhone,
            @Size(max = 190) String guardianEmail,
            @Size(max = 30) String guardianRelationship,
            @Size(max = 200) String previousSchoolName) {}

    public record TransitionAdmissionStatusRequest(
            @NotBlank String status,
            @Size(max = 1000) String reviewNotes) {}

    public record EnrollApprovedApplicantRequest(
            Long sectionId,
            @Min(1) int rollNumber) {}

    public record CreateStudentWithEnrollmentRequest(
            @Size(max = 40) String studentCode,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull LocalDate dateOfBirth,
            @NotBlank @Size(max = 20) String gender,
            @Size(max = 10) String bloodGroup,
            @Size(max = 190) String email,
            @Size(max = 30) String phone,
            @NotBlank @Size(max = 200) String guardianName,
            @NotBlank @Size(max = 30) String guardianPhone,
            @Size(max = 190) String guardianEmail,
            @Size(max = 30) String guardianRelationship,
            @NotNull Long academicSessionId,
            Long campusId,
            @NotNull Long classId,
            @NotNull Long sectionId,
            @Min(1) int rollNumber) {}

    public record PromoteBatchRequest(
            @NotNull Long sourceSessionId,
            @NotNull Long sourceClassId,
            @NotNull Long targetSessionId,
            @NotNull Long targetClassId,
            @NotNull Long targetSectionId) {}

    public record StudentDetailDto(
            Student student,
            List<Enrollment> enrollments,
            List<Guardian> guardians) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    @PostMapping("/admissions/applications")
    @PreAuthorize("@perm.has(authentication, 'admission.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create and submit an admission application")
    public AdmissionApplication createApplication(@Valid @RequestBody CreateAdmissionApplicationRequest request) {
        return studentService.createAdmissionApplication(
                request.academicSessionId(),
                request.targetCampusId(),
                request.targetClassId(),
                request.targetSectionId(),
                request.applicantFirstName(),
                request.applicantLastName(),
                request.dateOfBirth(),
                request.gender(),
                request.guardianName(),
                request.guardianPhone(),
                request.guardianEmail(),
                request.guardianRelationship(),
                request.previousSchoolName());
    }

    @GetMapping("/admissions/applications")
    @PreAuthorize("@perm.has(authentication, 'admission.read')")
    @Operation(summary = "List admission applications")
    public PageResponse<AdmissionApplication> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<AdmissionApplication> result = studentService.listAdmissionApplications(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/admissions/applications/{id}/status")
    @PreAuthorize("@perm.has(authentication, 'admission.review')")
    @Operation(summary = "Review and transition admission application status")
    public AdmissionApplication transitionApplicationStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody TransitionAdmissionStatusRequest request) {
        Long reviewerId = principal != null ? principal.userId() : null;
        return studentService.transitionAdmissionStatus(id, request.status(), reviewerId, request.reviewNotes());
    }

    @PostMapping("/admissions/applications/{id}/enroll")
    @PreAuthorize("@perm.has(authentication, 'admission.enroll')")
    @Operation(summary = "Convert an APPROVED application into Student, Guardian, and Enrollment")
    public StudentDetailDto enrollApplicant(
            @PathVariable Long id,
            @Valid @RequestBody EnrollApprovedApplicantRequest request) {
        Student student = studentService.enrollApprovedApplicant(id, request.sectionId(), request.rollNumber());
        return new StudentDetailDto(
                student,
                studentService.listStudentEnrollments(student.getId()),
                studentService.listStudentGuardians(student.getId()));
    }

    @PostMapping("/students")
    @PreAuthorize("@perm.has(authentication, 'student.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a student with guardian link and initial session enrollment")
    public StudentDetailDto createStudent(@Valid @RequestBody CreateStudentWithEnrollmentRequest request) {
        Student student = studentService.createStudentWithEnrollment(
                request.studentCode(),
                request.firstName(),
                request.lastName(),
                request.dateOfBirth(),
                request.gender(),
                request.bloodGroup(),
                request.email(),
                request.phone(),
                request.guardianName(),
                request.guardianPhone(),
                request.guardianEmail(),
                request.guardianRelationship(),
                request.academicSessionId(),
                request.campusId(),
                request.classId(),
                request.sectionId(),
                request.rollNumber());
        return new StudentDetailDto(
                student,
                studentService.listStudentEnrollments(student.getId()),
                studentService.listStudentGuardians(student.getId()));
    }

    @GetMapping("/students")
    @PreAuthorize("@perm.has(authentication, 'student.read')")
    @Operation(summary = "List students")
    public PageResponse<Student> listStudents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<Student> result = studentService.listStudents(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/students/{id}")
    @PreAuthorize("@perm.has(authentication, 'student.read')")
    @Operation(summary = "Get student profile with enrollments and guardians")
    public StudentDetailDto getStudent(@PathVariable Long id) {
        Student student = studentService.getStudentOrThrow(id);
        return new StudentDetailDto(
                student,
                studentService.listStudentEnrollments(id),
                studentService.listStudentGuardians(id));
    }

    @DeleteMapping("/students/{id}")
    @PreAuthorize("@perm.has(authentication, 'student.delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive (soft-delete) a student record")
    public void archiveStudent(@PathVariable Long id) {
        studentService.archiveStudent(id);
    }

    @GetMapping("/students/{id}/enrollments")
    @PreAuthorize("@perm.has(authentication, 'enrollment.read')")
    @Operation(summary = "List session enrollment history for a student")
    public List<Enrollment> listStudentEnrollments(@PathVariable Long id) {
        return studentService.listStudentEnrollments(id);
    }

    @GetMapping("/enrollments")
    @PreAuthorize("@perm.has(authentication, 'enrollment.read')")
    @Operation(summary = "List active enrollments by session, class, or section")
    public List<Enrollment> listEnrollments(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long sectionId) {
        return studentService.listSessionEnrollments(sessionId, classId, sectionId);
    }

    @PostMapping("/enrollments/promotions/commit")
    @PreAuthorize("@perm.has(authentication, 'enrollment.promote')")
    @Operation(summary = "Promote active students from source session/class to target session/class/section")
    public List<Enrollment> promoteBatch(@Valid @RequestBody PromoteBatchRequest request) {
        return studentService.promoteBatch(
                request.sourceSessionId(),
                request.sourceClassId(),
                request.targetSessionId(),
                request.targetClassId(),
                request.targetSectionId());
    }

    @GetMapping("/guardians")
    @PreAuthorize("@perm.has(authentication, 'guardian.read')")
    @Operation(summary = "List guardians")
    public PageResponse<Guardian> listGuardians(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<Guardian> result = studentService.listGuardians(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}

