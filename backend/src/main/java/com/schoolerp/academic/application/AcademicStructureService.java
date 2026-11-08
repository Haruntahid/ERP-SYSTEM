package com.schoolerp.academic.application;

import com.schoolerp.academic.domain.AcademicClass;
import com.schoolerp.academic.domain.Campus;
import com.schoolerp.academic.domain.ClassSubject;
import com.schoolerp.academic.domain.Department;
import com.schoolerp.academic.domain.Room;
import com.schoolerp.academic.domain.Section;
import com.schoolerp.academic.domain.Shift;
import com.schoolerp.academic.domain.Subject;
import com.schoolerp.academic.infrastructure.AcademicClassRepository;
import com.schoolerp.academic.infrastructure.CampusRepository;
import com.schoolerp.academic.infrastructure.ClassSubjectRepository;
import com.schoolerp.academic.infrastructure.DepartmentRepository;
import com.schoolerp.academic.infrastructure.RoomRepository;
import com.schoolerp.academic.infrastructure.SectionRepository;
import com.schoolerp.academic.infrastructure.ShiftRepository;
import com.schoolerp.academic.infrastructure.SubjectRepository;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicStructureService {

    private final CampusRepository campusRepository;
    private final DepartmentRepository departmentRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final ShiftRepository shiftRepository;
    private final RoomRepository roomRepository;

    public AcademicStructureService(
            CampusRepository campusRepository,
            DepartmentRepository departmentRepository,
            AcademicClassRepository academicClassRepository,
            SectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            ClassSubjectRepository classSubjectRepository,
            ShiftRepository shiftRepository,
            RoomRepository roomRepository) {
        this.campusRepository = campusRepository;
        this.departmentRepository = departmentRepository;
        this.academicClassRepository = academicClassRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.shiftRepository = shiftRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public Campus getDefaultCampus() {
        return campusRepository.findByCodeAndDeletedAtIsNull("MAIN")
                .orElseGet(() -> campusRepository.findAll().stream().filter(c -> c.getDeletedAt() == null).findFirst()
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No campus configured")));
    }

    @Transactional(readOnly = true)
    public Department getDefaultDepartment() {
        return departmentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .filter(d -> d.getCampus() != null && "MAIN".equals(d.getCampus().getCode()))
                .findFirst()
                .orElseGet(() -> departmentRepository.findAll().stream()
                        .filter(d -> d.getDeletedAt() == null)
                        .findFirst()
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No department configured")));
    }

    @Transactional(readOnly = true)
    public Page<Campus> listCampuses(Pageable pageable) {
        return campusRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Department> listDepartments(Pageable pageable) {
        return departmentRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AcademicClass> listClasses(Pageable pageable) {
        return academicClassRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public List<Section> listSectionsByClass(Long classId) {
        if (classId == null) {
            return sectionRepository.findAllByDeletedAtIsNull(Pageable.ofSize(100)).getContent();
        }
        return sectionRepository.findAllByAcademicClassIdAndDeletedAtIsNull(classId);
    }

    @Transactional(readOnly = true)
    public Page<Subject> listSubjects(Pageable pageable) {
        return subjectRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public List<ClassSubject> listSubjectsForClass(Long classId) {
        return classSubjectRepository.findAllByAcademicClassIdAndDeletedAtIsNull(classId);
    }

    @Transactional(readOnly = true)
    public List<Shift> listShifts() {
        return shiftRepository.findAllByDeletedAtIsNullOrderByStartTimeAsc();
    }

    @Transactional(readOnly = true)
    public Page<Room> listRooms(Pageable pageable) {
        return roomRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional
    public Campus saveCampus(String code, String name, String address, String phone) {
        Optional<Campus> existing = campusRepository.findByCodeAndDeletedAtIsNull(code);
        if (existing.isPresent()) {
            Campus campus = existing.get();
            campus.setName(name);
            campus.setAddress(address);
            campus.setPhone(phone);
            return campusRepository.save(campus);
        }
        return campusRepository.save(new Campus(code, name));
    }

    @Transactional
    public Department saveDepartment(Long campusId, String code, String name) {
        Campus campus = campusRepository.findByIdAndDeletedAtIsNull(campusId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campus not found"));
        if (departmentRepository.findByCampusIdAndCodeAndDeletedAtIsNull(campusId, code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Department already exists in campus");
        }
        return departmentRepository.save(new Department(campus, code, name));
    }

    @Transactional
    public AcademicClass saveClass(Long departmentId, String code, String name, int numericLevel) {
        Department department = departmentRepository.findByIdAndDeletedAtIsNull(departmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        if (academicClassRepository.findByDepartmentIdAndCodeAndDeletedAtIsNull(departmentId, code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Class code already exists in department");
        }
        return academicClassRepository.save(new AcademicClass(department, code, name, numericLevel));
    }

    @Transactional
    public Section saveSection(Long classId, Long shiftId, Long roomId, String code, String name, int capacity) {
        AcademicClass academicClass = academicClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class not found"));
        Shift shift = shiftId == null ? null : shiftRepository.findByIdAndDeletedAtIsNull(shiftId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shift not found"));
        Room room = roomId == null ? null : roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
        if (sectionRepository.findByAcademicClassIdAndCodeAndDeletedAtIsNull(classId, code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Section code already exists in class");
        }
        Section section = new Section(academicClass, code, name);
        section.setShift(shift);
        section.setRoom(room);
        section.setCapacity(capacity);
        return sectionRepository.save(section);
    }

    @Transactional
    public Subject saveSubject(String code, String name, String subjectType, BigDecimal creditHours) {
        if (subjectRepository.findByCodeAndDeletedAtIsNull(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Subject code already exists");
        }
        return subjectRepository.save(new Subject(code, name, subjectType, creditHours));
    }

    @Transactional
    public ClassSubject assignSubjectToClass(Long classId, Long subjectId, boolean mandatory, BigDecimal maxMarks, BigDecimal passMarks, int displayOrder) {
        AcademicClass academicClass = academicClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class not found"));
        Subject subject = subjectRepository.findByIdAndDeletedAtIsNull(subjectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found"));
        Optional<ClassSubject> existing = classSubjectRepository.findByAcademicClassIdAndSubjectIdAndDeletedAtIsNull(classId, subjectId);
        if (existing.isPresent()) {
            ClassSubject cs = existing.get();
            cs.setMandatory(mandatory);
            cs.setMaxMarks(maxMarks);
            cs.setPassMarks(passMarks);
            cs.setDisplayOrder(displayOrder);
            return classSubjectRepository.save(cs);
        }
        ClassSubject classSubject = new ClassSubject(academicClass, subject);
        classSubject.setMandatory(mandatory);
        classSubject.setMaxMarks(maxMarks);
        classSubject.setPassMarks(passMarks);
        classSubject.setDisplayOrder(displayOrder);
        return classSubjectRepository.save(classSubject);
    }

    @Transactional
    public Shift saveShift(String code, String name, LocalTime startTime, LocalTime endTime) {
        if (shiftRepository.findByCodeAndDeletedAtIsNull(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Shift code already exists");
        }
        return shiftRepository.save(new Shift(code, name, startTime, endTime));
    }

    @Transactional
    public Room saveRoom(Long campusId, String roomNumber, String name, String roomType, int capacity) {
        Campus campus = campusRepository.findByIdAndDeletedAtIsNull(campusId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campus not found"));
        if (roomRepository.findByCampusIdAndRoomNumberAndDeletedAtIsNull(campusId, roomNumber).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Room already exists in campus");
        }
        Room room = new Room(campus, roomNumber, name);
        room.setRoomType(roomType == null || roomType.isBlank() ? "CLASSROOM" : roomType);
        room.setCapacity(capacity > 0 ? capacity : 40);
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteClass(Long id) {
        AcademicClass academicClass = academicClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class not found"));
        academicClass.softDelete();
        academicClassRepository.save(academicClass);
    }

    @Transactional
    public void deleteSection(Long id) {
        Section section = sectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Section not found"));
        section.softDelete();
        sectionRepository.save(section);
    }

    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = subjectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found"));
        subject.softDelete();
        subjectRepository.save(subject);
    }

    public String tenantSlug() {
        return TenantContext.get().orElse("system");
    }
}

