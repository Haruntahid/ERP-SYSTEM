package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByIdAndDeletedAtIsNull(Long id);
    Page<Department> findAllByDeletedAtIsNull(Pageable pageable);
    List<Department> findAllByCampusIdAndDeletedAtIsNull(Long campusId);
    Optional<Department> findByCampusIdAndCodeAndDeletedAtIsNull(Long campusId, String code);
}

