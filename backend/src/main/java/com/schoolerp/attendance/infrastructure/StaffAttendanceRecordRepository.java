package com.schoolerp.attendance.infrastructure;

import com.schoolerp.attendance.domain.StaffAttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAttendanceRecordRepository extends JpaRepository<StaffAttendanceRecord, Long> {
    List<StaffAttendanceRecord> findAllByAttendanceDate(LocalDate attendanceDate);
    Optional<StaffAttendanceRecord> findByStaffIdAndAttendanceDate(Long staffId, LocalDate attendanceDate);
}

