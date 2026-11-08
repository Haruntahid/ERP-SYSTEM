package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByIdAndDeletedAtIsNull(Long id);
    Page<Invoice> findAllByDeletedAtIsNull(Pageable pageable);
    List<Invoice> findAllByStudentIdAndDeletedAtIsNullOrderByIdDesc(Long studentId);
}

