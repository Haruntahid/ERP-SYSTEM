package com.schoolerp.academic.api;

import com.schoolerp.academic.application.AcademicSessionService;
import com.schoolerp.academic.application.AcademicStructureService;
import com.schoolerp.academic.domain.AcademicClass;
import com.schoolerp.academic.domain.AcademicSession;
import com.schoolerp.academic.domain.Campus;
import com.schoolerp.academic.domain.ClassSubject;
import com.schoolerp.academic.domain.Department;
import com.schoolerp.academic.domain.Room;
import com.schoolerp.academic.domain.Section;
import com.schoolerp.academic.domain.Shift;
import com.schoolerp.academic.domain.Subject;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.RequiresFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/academic")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Academic Structure", description = "Academic sessions, classes, sections, subjects, and rooms")
@RequiresFeature("academic")
public class AcademicController {

    private final AcademicSessionService academicSessionService;
    private final AcademicStructureService academicStructureService;

    public AcademicController(AcademicSessionService academicSessionService, AcademicStructureService academicStructureService) {
        this.academicSessionService = academicSessionService;
        this.academicStructureService = academicStructureService;
    }

    public record CreateAcademicSessionRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 20) String sessionType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {}

    public record SaveCampusRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String address, @Size(max = 40) String phone) {}

    public record SaveDepartmentRequest(@NotNull Long campusId, @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name) {}

    public record SaveAcademicClassRequest(@NotNull Long departmentId, @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name, @Min(1) int numericLevel) {}

    public record SaveSectionRequest(@NotNull Long classId, Long shiftId, Long roomId,
            @NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 100) String name,
            @Min(1) int capacity) {}

    public record SaveSubjectRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String subjectType, @NotNull @DecimalMin("0.00") BigDecimal creditHours) {}

    public record AssignClassSubjectsRequest(Long classId, Long subjectId, boolean mandatory,
            @NotNull @DecimalMin("0.00") BigDecimal maxMarks,
            @NotNull @DecimalMin("0.00") BigDecimal passMarks, @Min(1) int displayOrder) {}

    public record SaveShiftRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 100) String name,
            @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    public record SaveRoomRequest(@NotNull Long campusId, @NotBlank @Size(max = 40) String roomNumber,
            @Size(max = 120) String name, @Size(max = 30) String roomType, @Min(1) int capacity) {}

    @GetMapping("/sessions")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List academic sessions")
    public PageResponse<AcademicSessionSummary> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<AcademicSession> pageResult = academicSessionService.list(pageable);
        return new PageResponse<>(
                pageResult.getContent().stream().map(AcademicSessionSummary::from).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages());
    }

    @PostMapping("/sessions")
    @PreAuthorize("@perm.has(authentication, 'academic.session.manage')")
    @Operation(summary = "Create academic session")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicSessionSummary createSession(@Valid @RequestBody CreateAcademicSessionRequest request) {
        AcademicSession saved = academicSessionService.create(
                request.code(), request.name(), request.sessionType(), request.startDate(), request.endDate());
        return AcademicSessionSummary.from(saved);
    }

    @PostMapping("/sessions/{id}/activate")
    @PreAuthorize("@perm.has(authentication, 'academic.session.manage')")
    @Operation(summary = "Activate an academic session")
    public AcademicSessionSummary activateSession(@PathVariable Long id) {
        return AcademicSessionSummary.from(academicSessionService.activate(id));
    }

    @PostMapping("/sessions/{id}/archive")
    @PreAuthorize("@perm.has(authentication, 'academic.session.manage')")
    @Operation(summary = "Archive an academic session")
    public AcademicSessionSummary archiveSession(@PathVariable Long id) {
        return AcademicSessionSummary.from(academicSessionService.archive(id));
    }

    @GetMapping("/campuses")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List campuses")
    public PageResponse<CampusSummary> listCampuses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Campus> pageResult = academicStructureService.listCampuses(PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(pageResult.getContent().stream().map(CampusSummary::from).toList(), page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @PostMapping("/campuses")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create or update campus")
    @ResponseStatus(HttpStatus.CREATED)
    public CampusSummary createCampus(@Valid @RequestBody SaveCampusRequest request) {
        return CampusSummary.from(academicStructureService.saveCampus(request.code(), request.name(), request.address(), request.phone()));
    }

    @GetMapping("/departments")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List departments")
    public PageResponse<DepartmentSummary> listDepartments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long campusId) {
        Page<Department> pageResult = academicStructureService.listDepartments(PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(pageResult.getContent().stream().map(DepartmentSummary::from).toList(), page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @PostMapping("/departments")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create department")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentSummary createDepartment(@Valid @RequestBody SaveDepartmentRequest request) {
        return DepartmentSummary.from(academicStructureService.saveDepartment(request.campusId(), request.code(), request.name()));
    }

    @GetMapping("/classes")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List classes")
    public PageResponse<AcademicClassSummary> listClasses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AcademicClass> pageResult = academicStructureService.listClasses(PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(pageResult.getContent().stream().map(AcademicClassSummary::from).toList(), page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @PostMapping("/classes")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create academic class")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicClassSummary createClass(@Valid @RequestBody SaveAcademicClassRequest request) {
        return AcademicClassSummary.from(academicStructureService.saveClass(request.departmentId(), request.code(), request.name(), request.numericLevel()));
    }

    @DeleteMapping("/classes/{id}")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete an academic class")
    public void deleteClass(@PathVariable Long id) {
        academicStructureService.deleteClass(id);
    }

    @GetMapping("/sections")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List sections")
    public List<SectionSummary> listSections(@RequestParam(required = false) Long classId) {
        return academicStructureService.listSectionsByClass(classId).stream().map(SectionSummary::from).toList();
    }

    @PostMapping("/sections")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create section")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionSummary createSection(@Valid @RequestBody SaveSectionRequest request) {
        return SectionSummary.from(academicStructureService.saveSection(request.classId(), request.shiftId(), request.roomId(), request.code(), request.name(), request.capacity()));
    }

    @DeleteMapping("/sections/{id}")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a section")
    public void deleteSection(@PathVariable Long id) {
        academicStructureService.deleteSection(id);
    }

    @GetMapping("/subjects")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List subjects")
    public PageResponse<SubjectSummary> listSubjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Subject> pageResult = academicStructureService.listSubjects(PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(pageResult.getContent().stream().map(SubjectSummary::from).toList(), page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @PostMapping("/subjects")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create subject")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectSummary createSubject(@Valid @RequestBody SaveSubjectRequest request) {
        return SubjectSummary.from(academicStructureService.saveSubject(request.code(), request.name(), request.subjectType(), request.creditHours()));
    }

    @GetMapping("/classes/{classId}/subjects")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List class subjects")
    public List<ClassSubjectSummary> listClassSubjects(@PathVariable Long classId) {
        return academicStructureService.listSubjectsForClass(classId).stream().map(ClassSubjectSummary::from).toList();
    }

    @PutMapping("/classes/{classId}/subjects")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Assign subject to class")
    public List<ClassSubjectSummary> assignClassSubjects(@PathVariable Long classId, @Valid @RequestBody AssignClassSubjectsRequest request) {
        academicStructureService.assignSubjectToClass(classId, request.subjectId(), request.mandatory(), request.maxMarks(), request.passMarks(), request.displayOrder());
        return academicStructureService.listSubjectsForClass(classId).stream().map(ClassSubjectSummary::from).toList();
    }

    @GetMapping("/shifts")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List shifts")
    public List<ShiftSummary> listShifts() {
        return academicStructureService.listShifts().stream().map(ShiftSummary::from).toList();
    }

    @PostMapping("/shifts")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create shift")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftSummary createShift(@Valid @RequestBody SaveShiftRequest request) {
        return ShiftSummary.from(academicStructureService.saveShift(request.code(), request.name(), request.startTime(), request.endTime()));
    }

    @GetMapping("/rooms")
    @PreAuthorize("@perm.has(authentication, 'academic.read')")
    @Operation(summary = "List rooms")
    public PageResponse<RoomSummary> listRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Room> pageResult = academicStructureService.listRooms(PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(pageResult.getContent().stream().map(RoomSummary::from).toList(), page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @PostMapping("/rooms")
    @PreAuthorize("@perm.has(authentication, 'academic.write')")
    @Operation(summary = "Create room")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomSummary createRoom(@Valid @RequestBody SaveRoomRequest request) {
        return RoomSummary.from(academicStructureService.saveRoom(request.campusId(), request.roomNumber(), request.name(), request.roomType(), request.capacity()));
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record AcademicSessionSummary(Long id, String code, String name, String sessionType, LocalDate startDate, LocalDate endDate,
            String status, boolean current) {
        static AcademicSessionSummary from(AcademicSession session) {
            return new AcademicSessionSummary(session.getId(), session.getCode(), session.getName(), session.getSessionType(),
                    session.getStartDate(), session.getEndDate(), session.getStatus(), session.isCurrent());
        }
    }

    public record CampusSummary(Long id, String code, String name, String address, String phone) {
        static CampusSummary from(Campus campus) { return new CampusSummary(campus.getId(), campus.getCode(), campus.getName(), campus.getAddress(), campus.getPhone()); }
    }

    public record DepartmentSummary(Long id, Long campusId, String code, String name) {
        static DepartmentSummary from(Department department) { return new DepartmentSummary(department.getId(), department.getCampus().getId(), department.getCode(), department.getName()); }
    }

    public record AcademicClassSummary(Long id, Long departmentId, String code, String name, int numericLevel) {
        static AcademicClassSummary from(AcademicClass academicClass) { return new AcademicClassSummary(academicClass.getId(), academicClass.getDepartment().getId(), academicClass.getCode(), academicClass.getName(), academicClass.getNumericLevel()); }
    }

    public record SectionSummary(Long id, Long classId, String code, String name, int capacity) {
        static SectionSummary from(Section section) { return new SectionSummary(section.getId(), section.getAcademicClass().getId(), section.getCode(), section.getName(), section.getCapacity()); }
    }

    public record SubjectSummary(Long id, String code, String name, String subjectType, BigDecimal creditHours) {
        static SubjectSummary from(Subject subject) { return new SubjectSummary(subject.getId(), subject.getCode(), subject.getName(), subject.getSubjectType(), subject.getCreditHours()); }
    }

    public record ClassSubjectSummary(Long id, Long classId, Long subjectId, String subjectName, boolean mandatory, BigDecimal maxMarks, BigDecimal passMarks, int displayOrder) {
        static ClassSubjectSummary from(ClassSubject cs) { return new ClassSubjectSummary(cs.getId(), cs.getAcademicClass().getId(), cs.getSubject().getId(), cs.getSubject().getName(), cs.isMandatory(), cs.getMaxMarks(), cs.getPassMarks(), cs.getDisplayOrder()); }
    }

    public record ShiftSummary(Long id, String code, String name, LocalTime startTime, LocalTime endTime) {
        static ShiftSummary from(Shift shift) { return new ShiftSummary(shift.getId(), shift.getCode(), shift.getName(), shift.getStartTime(), shift.getEndTime()); }
    }

    public record RoomSummary(Long id, Long campusId, String roomNumber, String name, String roomType, int capacity) {
        static RoomSummary from(Room room) { return new RoomSummary(room.getId(), room.getCampus().getId(), room.getRoomNumber(), room.getName(), room.getRoomType(), room.getCapacity()); }
    }
}

