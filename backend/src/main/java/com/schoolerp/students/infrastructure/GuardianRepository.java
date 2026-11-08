package com.schoolerp.students.infrastructure;

import com.schoolerp.students.domain.Guardian;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
    Optional<Guardian> findByIdAndDeletedAtIsNull(Long id);
    Optional<Guardian> findFirstByPhoneAndDeletedAtIsNull(String phone);
    Page<Guardian> findAllByDeletedAtIsNull(Pageable pageable);
}

