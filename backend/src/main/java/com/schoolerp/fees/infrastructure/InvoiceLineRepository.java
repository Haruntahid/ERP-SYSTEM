package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.InvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    List<InvoiceLine> findAllByInvoiceIdOrderByIdAsc(Long invoiceId);
}

