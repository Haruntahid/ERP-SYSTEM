package com.schoolerp.website;

import com.schoolerp.academic.application.AcademicSessionService;
import com.schoolerp.academic.application.AcademicStructureService;
import com.schoolerp.notices.application.NoticeService;
import com.schoolerp.notices.domain.Notice;
import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.staff.application.StaffService;
import com.schoolerp.staff.domain.Designation;
import com.schoolerp.staff.domain.Staff;
import com.schoolerp.students.application.StudentService;
import com.schoolerp.students.domain.AdmissionApplication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public unauthenticated endpoints powering each school's dynamic public website template
 * (branding, principal message, faculty directory, classes, public notices, and online admission).
 */
@Validated
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public School Website", description = "Public school website data, faculty showcase, notices, and online admission")
public class PublicSchoolWebsiteController {

    private final SettingsService settingsService;
    private final TenantRegistry tenantRegistry;
    private final AcademicSessionService academicSessionService;
    private final AcademicStructureService academicService;
    private final StaffService staffService;
    private final NoticeService noticeService;
    private final StudentService studentService;

    public PublicSchoolWebsiteController(
            SettingsService settingsService,
            TenantRegistry tenantRegistry,
            AcademicSessionService academicSessionService,
            AcademicStructureService academicService,
            StaffService staffService,
            NoticeService noticeService,
            StudentService studentService) {
        this.settingsService = settingsService;
        this.tenantRegistry = tenantRegistry;
        this.academicSessionService = academicSessionService;
        this.academicService = academicService;
        this.staffService = staffService;
        this.noticeService = noticeService;
        this.studentService = studentService;
    }

    public record PublicSessionDto(Long id, String code, String name, boolean current) {}

    public record PublicCampusDto(Long id, String code, String name, String address, String phone) {}

    public record PublicClassDto(Long id, String code, String name, int numericLevel) {}

    public record PublicSectionDto(Long id, Long classId, String code, String name, int capacity) {}

    public record PublicSubjectDto(Long id, String code, String name) {}

    public record PublicTeacherDto(
            Long id,
            String employeeCode,
            String fullName,
            String staffType,
            String designationTitle,
            String qualification,
            String specialization,
            String email) {}

    public record PublicStatsDto(
            long totalStudents,
            long totalTeachers,
            long totalClasses,
            long totalCampuses) {}

    public record PublicSchoolWebsiteResponse(
            String slug,
            String schoolName,
            String plan,
            String primaryColor,
            String accentColor,
            String logoUrl,
            String defaultLocale,
            String heroTitle,
            String heroSubtitle,
            String aboutText,
            String principalName,
            String principalMessage,
            String contactPhone,
            String contactEmail,
            String contactAddress,
            String establishedYear,
            String eiinNumber,
            PublicStatsDto stats,
            List<PublicSessionDto> sessions,
            List<PublicCampusDto> campuses,
            List<PublicClassDto> classes,
            List<PublicSectionDto> sections,
            List<PublicSubjectDto> subjects,
            List<PublicTeacherDto> teachers,
            List<Notice> notices) {}

    public record PublicAdmissionApplyRequest(
            @NotNull Long academicSessionId,
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

    @GetMapping("/school-site")
    @Transactional(readOnly = true)
    @Operation(summary = "Get public school website data (branding, faculty, academic classes, notices, stats)")
    public PublicSchoolWebsiteResponse getSchoolWebsite() {
        String slug = TenantContext.require();
        TenantInfo tenant = tenantRegistry.find(slug).orElse(null);
        Map<String, String> settings = settingsService.getCachedSettingsMap();

        String schoolName = settings.getOrDefault(
                "school.display_name", tenant != null ? tenant.name() : slug.toUpperCase());
        String primaryColor = settings.getOrDefault("brand.primary_color", "#1d4ed8");
        String accentColor = settings.getOrDefault("brand.accent_color", "#f59e0b");
        String logoUrl = settings.getOrDefault("brand.logo_url", "");
        String defaultLocale = settings.getOrDefault("locale.default", "en");

        String heroTitle = settings.getOrDefault(
                "website.hero_title", "Inspiring Excellence, Shaping Future Leaders");
        String heroSubtitle = settings.getOrDefault(
                "website.hero_subtitle",
                "A modern digital campus connecting students, teachers, and guardians with world-class academics.");
        String aboutText = settings.getOrDefault(
                "website.about_text",
                "Our institution combines strong academic traditions with modern digital learning, character education, and transparent school governance.");
        String principalName = settings.getOrDefault("website.principal_name", "Principal");
        String principalMessage = settings.getOrDefault(
                "website.principal_message",
                "Welcome to our official school portal. Our mission is to nurture curious minds, ethical citizens, and lifelong learners.");
        String contactPhone = settings.getOrDefault("website.contact_phone", "+880 1700-000000");
        String contactEmail = settings.getOrDefault("website.contact_email", "info@" + slug + ".school");
        String contactAddress = settings.getOrDefault("website.contact_address", "Main Campus Road, Bangladesh");
        String establishedYear = settings.getOrDefault("website.established_year", "1995");
        String eiinNumber = settings.getOrDefault("website.eiin_number", "120456");

        List<PublicSessionDto> sessions = academicSessionService
                .list(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startDate")))
                .getContent()
                .stream()
                .map(s -> new PublicSessionDto(s.getId(), s.getCode(), s.getName(), s.isCurrent()))
                .toList();
        List<PublicCampusDto> campuses = academicService
                .listCampuses(PageRequest.of(0, 20, Sort.by("name")))
                .getContent()
                .stream()
                .map(c -> new PublicCampusDto(c.getId(), c.getCode(), c.getName(), c.getAddress(), c.getPhone()))
                .toList();
        List<PublicClassDto> classes = academicService
                .listClasses(PageRequest.of(0, 50, Sort.by("numericLevel", "name")))
                .getContent()
                .stream()
                .map(c -> new PublicClassDto(c.getId(), c.getCode(), c.getName(), c.getNumericLevel()))
                .toList();
        List<PublicSectionDto> sections = academicService.listSectionsByClass(null).stream()
                .map(s -> new PublicSectionDto(
                        s.getId(),
                        s.getAcademicClass() != null ? s.getAcademicClass().getId() : null,
                        s.getCode(),
                        s.getName(),
                        s.getCapacity()))
                .toList();
        List<PublicSubjectDto> subjects = academicService
                .listSubjects(PageRequest.of(0, 50, Sort.by("name")))
                .getContent()
                .stream()
                .map(sub -> new PublicSubjectDto(sub.getId(), sub.getCode(), sub.getName()))
                .toList();

        Map<Long, String> designationMap = new HashMap<>();
        for (Designation d : staffService.listDesignations(PageRequest.of(0, 100)).getContent()) {
            designationMap.put(d.getId(), d.getTitle());
        }

        List<Staff> staffRows = staffService
                .listStaff(PageRequest.of(0, 100, Sort.by("id")))
                .getContent();

        List<PublicTeacherDto> teachers = staffRows.stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getEmploymentStatus()))
                .map(s -> new PublicTeacherDto(
                        s.getId(),
                        s.getEmployeeCode(),
                        s.getFullName(),
                        s.getStaffType(),
                        designationMap.getOrDefault(s.getDesignationId(), "Faculty Member"),
                        s.getQualification(),
                        s.getSpecialization(),
                        s.getEmail()))
                .toList();

        List<Notice> notices = noticeService
                .listPublishedFeed(PageRequest.of(
                        0, 20, Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("id"))))
                .getContent();

        long totalStudents = studentService.listStudents(PageRequest.of(0, 1)).getTotalElements();

        PublicStatsDto stats = new PublicStatsDto(
                totalStudents,
                teachers.size(),
                classes.size(),
                Math.max(1, campuses.size()));

        return new PublicSchoolWebsiteResponse(
                slug,
                schoolName,
                tenant != null ? tenant.plan() : "ENTERPRISE",
                primaryColor,
                accentColor,
                logoUrl,
                defaultLocale,
                heroTitle,
                heroSubtitle,
                aboutText,
                principalName,
                principalMessage,
                contactPhone,
                contactEmail,
                contactAddress,
                establishedYear,
                eiinNumber,
                stats,
                sessions,
                campuses,
                classes,
                sections,
                subjects,
                teachers,
                notices);
    }

    @PostMapping("/admissions/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an online admission application from the school's public website")
    public AdmissionApplication applyForAdmission(@Valid @RequestBody PublicAdmissionApplyRequest request) {
        return studentService.createAdmissionApplication(
                request.academicSessionId(),
                null,
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
}
