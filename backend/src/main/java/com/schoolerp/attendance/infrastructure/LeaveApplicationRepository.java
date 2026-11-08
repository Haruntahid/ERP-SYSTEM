package com.schoolerp.attendance.infrastructure;

import com.schoolerp.attendance.domain.LeaveApplication;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {
    Optional<LeaveApplication> findByIdAndDeletedAtIsNull(Long id);
    Page<LeaveApplication> findAllByDeletedAtIsNull(Pageable pageable);
    List<LeaveApplication> findAllByStudentEnrollmentIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndDeletedAtIsNull(
            Long studentEnrollmentId, String status, LocalDate date1, LocalDate date2);
}

