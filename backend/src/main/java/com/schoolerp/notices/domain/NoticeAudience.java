package com.schoolerp.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notice_audiences")
public class NoticeAudience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_role_code", length = 60)
    private String targetRoleCode;

    @Column(name = "target_entity_id")
    private Long targetEntityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected NoticeAudience() {}

    public NoticeAudience(Long noticeId, String targetType, String targetRoleCode, Long targetEntityId) {
        this.noticeId = noticeId;
        this.targetType = targetType != null ? targetType.toUpperCase() : "WHOLE_SCHOOL";
        this.targetRoleCode = targetRoleCode;
        this.targetEntityId = targetEntityId;
    }

    public Long getId() { return id; }
    public Long getNoticeId() { return noticeId; }
    public String getTargetType() { return targetType; }
    public String getTargetRoleCode() { return targetRoleCode; }
    public Long getTargetEntityId() { return targetEntityId; }
}

