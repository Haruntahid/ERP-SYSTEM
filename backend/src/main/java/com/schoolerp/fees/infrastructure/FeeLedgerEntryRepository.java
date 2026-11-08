package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.FeeLedgerEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeLedgerEntryRepository extends JpaRepository<FeeLedgerEntry, Long> {
    List<FeeLedgerEntry> findAllByStudentIdOrderByIdDesc(Long studentId);
    Optional<FeeLedgerEntry> findFirstByStudentIdOrderByIdDesc(Long studentId);
}

