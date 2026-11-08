package com.schoolerp.students.infrastructure;

import com.schoolerp.students.domain.AdmissionApplication;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, Long> {
    Optional<AdmissionApplication> findByIdAndDeletedAtIsNull(Long id);
    Page<AdmissionApplication> findAllByDeletedAtIsNull(Pageable pageable);
    long countByDeletedAtIsNull();
}

