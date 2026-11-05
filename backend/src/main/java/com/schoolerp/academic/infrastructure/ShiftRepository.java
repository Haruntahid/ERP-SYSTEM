package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.Shift;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    List<Shift> findAllByDeletedAtIsNullOrderByStartTimeAsc();
    Optional<Shift> findByIdAndDeletedAtIsNull(Long id);
    Optional<Shift> findByCodeAndDeletedAtIsNull(String code);
}

