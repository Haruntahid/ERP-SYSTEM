package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.FeeStructure;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {
    List<FeeStructure> findAllByDeletedAtIsNullOrderByIdDesc();
    List<FeeStructure> findAllByAcademicSessionIdAndClassIdAndDeletedAtIsNull(Long academicSessionId, Long classId);
    Optional<FeeStructure> findByIdAndDeletedAtIsNull(Long id);
}

