package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.PaymentAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findAllByPaymentId(Long paymentId);
}

