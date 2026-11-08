package com.schoolerp.notices.infrastructure;

import com.schoolerp.notices.domain.NoticeAudience;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeAudienceRepository extends JpaRepository<NoticeAudience, Long> {
    List<NoticeAudience> findAllByNoticeId(Long noticeId);
}

