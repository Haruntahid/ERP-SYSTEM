package com.schoolerp.attendance.infrastructure;

import com.schoolerp.attendance.domain.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findByIdAndDeletedAtIsNull(Long id);
    Optional<AttendanceSession> findFirstByAcademicSessionIdAndSectionIdAndAttendanceDateAndDeletedAtIsNull(
            Long academicSessionId, Long sectionId, LocalDate attendanceDate);
    List<AttendanceSession> findAllByAcademicSessionIdAndSectionIdAndAttendanceDateBetweenAndDeletedAtIsNullOrderByAttendanceDateAsc(
            Long academicSessionId, Long sectionId, LocalDate fromDate, LocalDate toDate);
    List<AttendanceSession> findAllBySectionIdAndAttendanceDateBetweenAndDeletedAtIsNull(
            Long sectionId, LocalDate fromDate, LocalDate toDate);
}

