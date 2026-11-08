package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.FeeHead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeHeadRepository extends JpaRepository<FeeHead, Long> {
    List<FeeHead> findAllByDeletedAtIsNullOrderByAllocationPriorityAscIdAsc();
    Optional<FeeHead> findByCodeAndDeletedAtIsNull(String code);
}

