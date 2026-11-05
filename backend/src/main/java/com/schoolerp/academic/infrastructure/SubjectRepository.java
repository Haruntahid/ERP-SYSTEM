package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.Subject;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByIdAndDeletedAtIsNull(Long id);
    Page<Subject> findAllByDeletedAtIsNull(Pageable pageable);
    Optional<Subject> findByCodeAndDeletedAtIsNull(String code);
}

