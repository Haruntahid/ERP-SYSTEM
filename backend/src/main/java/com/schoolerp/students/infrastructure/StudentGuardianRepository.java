package com.schoolerp.students.infrastructure;

import com.schoolerp.students.domain.StudentGuardian;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, Long> {
    List<StudentGuardian> findAllByStudentId(Long studentId);
    List<StudentGuardian> findAllByGuardianId(Long guardianId);
    Optional<StudentGuardian> findByStudentIdAndGuardianId(Long studentId, Long guardianId);
}

