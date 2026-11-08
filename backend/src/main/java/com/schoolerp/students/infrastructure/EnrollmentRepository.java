package com.schoolerp.students.infrastructure;

import com.schoolerp.students.domain.Enrollment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Optional<Enrollment> findByIdAndDeletedAtIsNull(Long id);
    List<Enrollment> findAllByStudentIdAndDeletedAtIsNullOrderByIdDesc(Long studentId);
    Optional<Enrollment> findFirstByStudentIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
            Long studentId, Long academicSessionId, String status);
    boolean existsByAcademicSessionIdAndSectionIdAndRollNumberAndStatusAndDeletedAtIsNull(
            Long academicSessionId, Long sectionId, int rollNumber, String status);
    List<Enrollment> findAllByAcademicSessionIdAndClassIdAndSectionIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
            Long academicSessionId, Long classId, Long sectionId, String status);
    List<Enrollment> findAllByAcademicSessionIdAndSectionIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
            Long academicSessionId, Long sectionId, String status);
    List<Enrollment> findAllByAcademicSessionIdAndClassIdAndStatusAndDeletedAtIsNullOrderByRollNumberAsc(
            Long academicSessionId, Long classId, String status);
    List<Enrollment> findAllByAcademicSessionIdAndDeletedAtIsNullOrderByRollNumberAsc(Long academicSessionId);
}

