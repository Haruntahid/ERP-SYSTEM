package com.schoolerp.students.infrastructure;

import com.schoolerp.students.domain.Student;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByStudentCodeAndDeletedAtIsNull(String studentCode);
    Page<Student> findAllByDeletedAtIsNull(Pageable pageable);
    long countByDeletedAtIsNull();
}

