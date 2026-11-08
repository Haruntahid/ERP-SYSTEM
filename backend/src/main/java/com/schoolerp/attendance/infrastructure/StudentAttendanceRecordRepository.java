package com.schoolerp.attendance.infrastructure;

import com.schoolerp.attendance.domain.StudentAttendanceRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAttendanceRecordRepository extends JpaRepository<StudentAttendanceRecord, Long> {
    List<StudentAttendanceRecord> findAllByAttendanceSessionId(Long attendanceSessionId);
    Optional<StudentAttendanceRecord> findByAttendanceSessionIdAndEnrollmentId(Long attendanceSessionId, Long enrollmentId);
    List<StudentAttendanceRecord> findAllByStudentIdOrderByIdDesc(Long studentId);
    List<StudentAttendanceRecord> findAllByEnrollmentId(Long enrollmentId);
}

