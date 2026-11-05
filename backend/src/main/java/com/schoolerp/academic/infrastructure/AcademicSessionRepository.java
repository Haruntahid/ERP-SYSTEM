package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.AcademicSession;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {
    Page<AcademicSession> findAllByDeletedAtIsNull(Pageable pageable);
    Optional<AcademicSession> findByIdAndDeletedAtIsNull(Long id);
    Optional<AcademicSession> findByCodeAndDeletedAtIsNull(String code);
    Optional<AcademicSession> findByStatusAndDeletedAtIsNull(String status);
}

