package com.schoolerp.fees.infrastructure;

import com.schoolerp.fees.domain.StudentDiscountWaiver;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDiscountWaiverRepository extends JpaRepository<StudentDiscountWaiver, Long> {
    List<StudentDiscountWaiver> findAllByDeletedAtIsNullOrderByIdDesc();
    List<StudentDiscountWaiver> findAllByEnrollmentIdAndStatusAndDeletedAtIsNull(Long enrollmentId, String status);
}

