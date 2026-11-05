package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.Section;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
    Optional<Section> findByIdAndDeletedAtIsNull(Long id);
    Page<Section> findAllByDeletedAtIsNull(Pageable pageable);
    List<Section> findAllByAcademicClassIdAndDeletedAtIsNull(Long classId);
    Optional<Section> findByAcademicClassIdAndCodeAndDeletedAtIsNull(Long classId, String code);
}

