package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.ClassSubject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {
    List<ClassSubject> findAllByAcademicClassIdAndDeletedAtIsNull(Long academicClassId);
    Optional<ClassSubject> findByAcademicClassIdAndSubjectIdAndDeletedAtIsNull(Long academicClassId, Long subjectId);
}

