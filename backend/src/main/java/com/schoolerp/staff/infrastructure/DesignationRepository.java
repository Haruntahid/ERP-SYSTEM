package com.schoolerp.staff.infrastructure;

import com.schoolerp.staff.domain.Designation;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    Optional<Designation> findByCodeAndDeletedAtIsNull(String code);
    Optional<Designation> findByIdAndDeletedAtIsNull(Long id);
    Page<Designation> findAllByDeletedAtIsNull(Pageable pageable);
}

