package com.schoolerp.notices.infrastructure;

import com.schoolerp.notices.domain.NoticeReadReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeReadReceiptRepository extends JpaRepository<NoticeReadReceipt, Long> {
    Optional<NoticeReadReceipt> findByNoticeIdAndUserId(Long noticeId, Long userId);
    long countByNoticeId(Long noticeId);
}

