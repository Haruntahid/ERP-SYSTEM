package com.schoolerp.students.application;

import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.messaging.EventPriority;
import com.schoolerp.shared.messaging.OutboxEventWriter;
import com.schoolerp.students.domain.AdmissionApplication;
import com.schoolerp.students.domain.Enrollment;
import com.schoolerp.students.domain.Guardian;
import com.schoolerp.students.domain.Student;
import com.schoolerp.students.domain.StudentDocument;
import com.schoolerp.students.domain.StudentGuardian;
import com.schoolerp.students.infrastructure.AdmissionApplicationRepository;
import com.schoolerp.students.infrastructure.EnrollmentRepository;
import com.schoolerp.students.infrastructure.GuardianRepository;
import com.schoolerp.students.infrastructure.StudentDocumentRepository;
import com.schoolerp.students.infrastructure.StudentGuardianRepository;
import com.schoolerp.students.infrastructure.StudentRepository;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private static final Set<String> VALID_ADMISSION_STATUSES = Set.of(
            "DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "WAITLISTED", "REJECTED", "ENROLLED");

    private final StudentRepository studentRepository;
    private final GuardianRepository guardianRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AdmissionApplicationRepository admissionRepository;
    private final StudentDocumentRepository documentRepository;
    private final AuditService auditService;
    private final OutboxEventWriter outboxEventWriter;

    public StudentService(
            StudentRepository studentRepository,
            GuardianRepository guardianRepository,
            StudentGuardianRepository studentGuardianRepository,
            EnrollmentRepository enrollmentRepository,
            AdmissionApplicationRepository admissionRepository,
            StudentDocumentRepository documentRepository,
            AuditService auditService,
            OutboxEventWriter outboxEventWriter) {
        this.studentRepository = studentRepository;
        this.guardianRepository = guardianRepository;
        this.studentGuardianRepository = studentGuardianRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.admissionRepository = admissionRepository;
        this.documentRepository = documentRepository;
        this.auditService = auditService;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional(readOnly = true)
    public Page<AdmissionApplication> listAdmissionApplications(Pageable pageable) {
        return admissionRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public AdmissionApplication getAdmissionApplicationOrThrow(Long id) {
        return admissionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admission application not found"));
    }

    @Transactional
    public AdmissionApplication createAdmissionApplication(
            Long academicSessionId,
            Long targetCampusId,
            Long targetClassId,
            Long targetSectionId,
            String applicantFirstName,
            String applicantLastName,
            LocalDate dateOfBirth,
            String gender,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelationship,
            String previousSchoolName) {
        long seq = admissionRepository.count() + 1;
        String appNo = "ADM-%d-%05d".formatted(Year.now().getValue(), seq);

        AdmissionApplication app = new AdmissionApplication(
                appNo,
                academicSessionId,
                targetCampusId != null ? targetCampusId : 1L,
                targetClassId,
                targetSectionId,
                applicantFirstName.trim(),
                applicantLastName.trim(),
                dateOfBirth,
                gender.trim().toUpperCase(),
                guardianName.trim(),
                guardianPhone.trim(),
                guardianEmail,
                guardianRelationship,
                previousSchoolName);
        AdmissionApplication saved = admissionRepository.save(app);

        auditService.record("ADMISSION_SUBMITTED", "AdmissionApplication", String.valueOf(saved.getId()), null,
                Map.of("applicationNo", saved.getApplicationNo(), "applicantName", saved.getApplicantFullName()));
        outboxEventWriter.write("AdmissionApplication", String.valueOf(saved.getId()),
                "AdmissionApplicationSubmittedEvent", "admission.submitted", EventPriority.NORMAL,
                Map.of("applicationId", saved.getId(), "applicationNo", saved.getApplicationNo(),
                        "applicantName", saved.getApplicantFullName(), "guardianPhone", saved.getGuardianPhone()));
        return saved;
    }

    @Transactional
    public AdmissionApplication transitionAdmissionStatus(Long id, String newStatus, Long reviewerUserId, String reviewNotes) {
        AdmissionApplication app = getAdmissionApplicationOrThrow(id);
        String normalized = newStatus == null ? "" : newStatus.trim().toUpperCase();
        if (!VALID_ADMISSION_STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid admission status: " + newStatus);
        }
        if ("ENROLLED".equals(app.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Enrolled application cannot change status");
        }
        String oldStatus = app.getStatus();
        app.setStatus(normalized, reviewerUserId, reviewNotes);
        AdmissionApplication saved = admissionRepository.save(app);

        auditService.record("ADMISSION_STATUS_CHANGED", "AdmissionApplication", String.valueOf(saved.getId()),
                Map.of("status", oldStatus), Map.of("status", normalized, "reviewNotes", reviewNotes != null ? reviewNotes : ""));
        return saved;
    }

    @Transactional
    public Student enrollApprovedApplicant(Long applicationId, Long sectionId, int rollNumber) {
        AdmissionApplication app = getAdmissionApplicationOrThrow(applicationId);
        if ("ENROLLED".equals(app.getStatus()) && app.getEnrolledStudentId() != null) {
            return getStudentOrThrow(app.getEnrolledStudentId());
        }
        if (!"APPROVED".equals(app.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only APPROVED applications can be enrolled");
        }
        Long resolvedSectionId = sectionId != null ? sectionId : app.getTargetSectionId();
        if (resolvedSectionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target section ID is required to enroll applicant");
        }

        Student student = createStudentWithEnrollment(
                null,
                app.getApplicantFirstName(),
                app.getApplicantLastName(),
                app.getDateOfBirth(),
                app.getGender(),
                app.getBloodGroup(),
                null,
                null,
                app.getGuardianName(),
                app.getGuardianPhone(),
                app.getGuardianEmail(),
                app.getGuardianRelationship(),
                app.getAcademicSessionId(),
                app.getTargetCampusId(),
                app.getTargetClassId(),
                resolvedSectionId,
                rollNumber);

        app.markEnrolled(student.getId());
        admissionRepository.save(app);
        return student;
    }

    @Transactional
    public Student createStudentWithEnrollment(
            String customStudentCode,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            String bloodGroup,
            String email,
            String phone,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelationship,
            Long academicSessionId,
            Long campusId,
            Long classId,
            Long sectionId,
            int rollNumber) {
        if (enrollmentRepository.existsByAcademicSessionIdAndSectionIdAndRollNumberAndStatusAndDeletedAtIsNull(
                academicSessionId, sectionId, rollNumber, "ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "Roll number " + rollNumber + " is already taken in this section");
        }

        String studentCode = customStudentCode;
        if (studentCode == null || studentCode.isBlank()) {
            long seq = studentRepository.count() + 1;
            studentCode = "%d%02d%04d".formatted(Year.now().getValue(), classId % 100, seq);
            while (studentRepository.existsByStudentCodeAndDeletedAtIsNull(studentCode)) {
                seq++;
                studentCode = "%d%02d%04d".formatted(Year.now().getValue(), classId % 100, seq);
            }
        } else if (studentRepository.existsByStudentCodeAndDeletedAtIsNull(studentCode.trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Student code already exists: " + studentCode);
        }

        Student student = new Student(
                studentCode.trim(),
                firstName.trim(),
                lastName.trim(),
                dateOfBirth,
                gender.trim().toUpperCase(),
                bloodGroup,
                email,
                phone,
                LocalDate.now());
        Student savedStudent = studentRepository.save(student);

        // Reuse or create Guardian by phone (BR-05 sibling guardian deduplication)
        Guardian guardian = guardianRepository.findFirstByPhoneAndDeletedAtIsNull(guardianPhone.trim())
                .orElseGet(() -> guardianRepository.save(new Guardian(
                        guardianName.trim(), guardianPhone.trim(), guardianEmail, null)));

        studentGuardianRepository.save(new StudentGuardian(
                savedStudent.getId(), guardian.getId(), guardianRelationship, true, true));

        Enrollment enrollment = new Enrollment(
                savedStudent.getId(),
                academicSessionId,
                campusId != null ? campusId : 1L,
                null,
                classId,
                sectionId,
                rollNumber,
                LocalDate.now(),
                null);
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        auditService.record("STUDENT_CREATED", "Student", String.valueOf(savedStudent.getId()), null,
                Map.of("studentCode", savedStudent.getStudentCode(), "fullName", savedStudent.getFullName(),
                        "enrollmentId", savedEnrollment.getId(), "sectionId", sectionId, "rollNumber", rollNumber));

        outboxEventWriter.write("Student", String.valueOf(savedStudent.getId()),
                "StudentEnrolledEvent", "student.enrolled", EventPriority.NORMAL,
                Map.of("studentId", savedStudent.getId(), "studentCode", savedStudent.getStudentCode(),
                        "enrollmentId", savedEnrollment.getId(), "academicSessionId", academicSessionId,
                        "classId", classId, "sectionId", sectionId, "rollNumber", rollNumber,
                        "primaryGuardianId", guardian.getId()));

        return savedStudent;
    }

    @Transactional(readOnly = true)
    public Page<Student> listStudents(Pageable pageable) {
        return studentRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Student getStudentOrThrow(Long id) {
        return studentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    @Transactional(readOnly = true)
    public List<Enrollment> listStudentEnrollments(Long studentId) {
        return enrollmentRepository.findAllByStudentIdAndDeletedAtIsNullOrderByIdDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> listSessionEnrollments(Long sessionId, Long classId, Long sectionId) {
        if (sessionId != null && classId != null && sectionId != null) {
            return enrollmentRepository.findAllByAcademicSessionIdAndClassIdAndSectionIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
                    sessionId, classId, sectionId, "ACTIVE");
        }
        if (sessionId != null && sectionId != null) {
            return enrollmentRepository.findAllByAcademicSessionIdAndSectionIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
                    sessionId, sectionId, "ACTIVE");
        }
        if (sessionId != null && classId != null) {
            return enrollmentRepository.findAllByAcademicSessionIdAndClassIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
                    sessionId, classId, "ACTIVE");
        }
        if (sessionId != null) {
            return enrollmentRepository.findAllByAcademicSessionIdAndDeletedAtIsNullOrderByRollNumberAsc(sessionId);
        }
        return enrollmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Guardian> listStudentGuardians(Long studentId) {
        List<StudentGuardian> links = studentGuardianRepository.findAllByStudentId(studentId);
        return links.stream()
                .map(link -> guardianRepository.findByIdAndDeletedAtIsNull(link.getGuardianId()).orElse(null))
                .filter(g -> g != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Guardian> listGuardians(Pageable pageable) {
        return guardianRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public List<StudentDocument> listStudentDocuments(Long studentId) {
        return documentRepository.findAllByStudentIdAndDeletedAtIsNull(studentId);
    }

    @Transactional
    public void archiveStudent(Long studentId) {
        Student student = getStudentOrThrow(studentId);
        student.softDelete();
        studentRepository.save(student);
        auditService.record("STUDENT_ARCHIVED", "Student", String.valueOf(studentId), null,
                Map.of("studentCode", student.getStudentCode()));
    }

    @Transactional
    public List<Enrollment> promoteBatch(
            Long sourceSessionId,
            Long sourceClassId,
            Long targetSessionId,
            Long targetClassId,
            Long targetSectionId) {
        if (sourceSessionId.equals(targetSessionId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source and target academic sessions must be different");
        }
        List<Enrollment> activeSource = enrollmentRepository
                .findAllByAcademicSessionIdAndClassIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
                        sourceSessionId, sourceClassId, "ACTIVE");
        List<Enrollment> created = new ArrayList<>();
        int nextRoll = 1;
        for (Enrollment oldEnrollment : activeSource) {
            if (enrollmentRepository.findFirstByStudentIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
                    oldEnrollment.getStudentId(), targetSessionId, "ACTIVE").isPresent()) {
                continue;
            }
            oldEnrollment.setStatus("PROMOTED");
            enrollmentRepository.save(oldEnrollment);

            while (enrollmentRepository.existsByAcademicSessionIdAndSectionIdAndRollNumberAndStatusAndDeletedAtIsNull(
                    targetSessionId, targetSectionId, nextRoll, "ACTIVE")) {
                nextRoll++;
            }
            Enrollment promoted = new Enrollment(
                    oldEnrollment.getStudentId(),
                    targetSessionId,
                    oldEnrollment.getCampusId(),
                    oldEnrollment.getDepartmentId(),
                    targetClassId,
                    targetSectionId,
                    nextRoll++,
                    LocalDate.now(),
                    oldEnrollment.getId());
            created.add(enrollmentRepository.save(promoted));
        }
        auditService.record("SESSION_PROMOTION_COMMITTED", "Enrollment", String.valueOf(targetSessionId), null,
                Map.of("sourceSessionId", sourceSessionId, "targetSessionId", targetSessionId,
                        "promotedCount", created.size()));
        return created;
    }
}

