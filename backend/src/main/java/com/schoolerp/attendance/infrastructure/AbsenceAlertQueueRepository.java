package com.schoolerp.attendance.infrastructure;

import com.schoolerp.attendance.domain.AbsenceAlertQueue;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceAlertQueueRepository extends JpaRepository<AbsenceAlertQueue, Long> {
    Optional<AbsenceAlertQueue> findByStudentAttendanceRecordId(Long studentAttendanceRecordId);
}

