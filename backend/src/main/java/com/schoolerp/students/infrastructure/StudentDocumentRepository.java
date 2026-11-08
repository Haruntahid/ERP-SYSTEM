package com.schoolerp.students.infrastructure;

import com.schoolerp.students.domain.StudentDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {
    List<StudentDocument> findAllByStudentIdAndDeletedAtIsNull(Long studentId);
}

