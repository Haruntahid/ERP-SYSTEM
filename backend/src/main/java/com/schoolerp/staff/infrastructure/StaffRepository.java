package com.schoolerp.staff.infrastructure;

import com.schoolerp.staff.domain.Staff;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByIdAndDeletedAtIsNull(Long id);
    Optional<Staff> findByEmployeeCodeAndDeletedAtIsNull(String employeeCode);
    Page<Staff> findAllByDeletedAtIsNull(Pageable pageable);
    boolean existsByEmployeeCodeAndDeletedAtIsNull(String employeeCode);
}

