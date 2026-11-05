package com.schoolerp.staff.infrastructure;

import com.schoolerp.staff.domain.TeacherSubjectAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherSubjectAssignmentRepository extends JpaRepository<TeacherSubjectAssignment, Long> {
    List<TeacherSubjectAssignment> findAllByDeletedAtIsNull();
    Optional<TeacherSubjectAssignment> findByIdAndDeletedAtIsNull(Long id);
    List<TeacherSubjectAssignment> findAllByAcademicSessionIdAndSectionIdAndDeletedAtIsNull(Long academicSessionId, Long sectionId);
    List<TeacherSubjectAssignment> findAllByStaffIdAndDeletedAtIsNull(Long staffId);
}

