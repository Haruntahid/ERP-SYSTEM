package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.FeeStructureLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureLineRepository extends JpaRepository<FeeStructureLine, Long> {
    List<FeeStructureLine> findAllByFeeStructureId(Long feeStructureId);
}

