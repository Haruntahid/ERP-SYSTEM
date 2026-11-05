package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.AcademicClass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicClassRepository extends JpaRepository<AcademicClass, Long> {
    Optional<AcademicClass> findByIdAndDeletedAtIsNull(Long id);
    Page<AcademicClass> findAllByDeletedAtIsNull(Pageable pageable);
    List<AcademicClass> findAllByDepartmentIdAndDeletedAtIsNull(Long departmentId);
    Optional<AcademicClass> findByDepartmentIdAndCodeAndDeletedAtIsNull(Long departmentId, String code);
}

