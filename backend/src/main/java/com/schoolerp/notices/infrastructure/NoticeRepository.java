package com.schoolerp.notices.infrastructure;

import com.schoolerp.notices.domain.Notice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Optional<Notice> findByIdAndDeletedAtIsNull(Long id);
    Page<Notice> findAllByDeletedAtIsNull(Pageable pageable);
    Page<Notice> findAllByStatusAndDeletedAtIsNull(String status, Pageable pageable);
}

