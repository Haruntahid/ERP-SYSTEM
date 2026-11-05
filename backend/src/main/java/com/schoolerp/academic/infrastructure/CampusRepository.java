package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.Campus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {
    Optional<Campus> findByCodeAndDeletedAtIsNull(String code);
    Page<Campus> findAllByDeletedAtIsNull(Pageable pageable);
    Optional<Campus> findByIdAndDeletedAtIsNull(Long id);
}

