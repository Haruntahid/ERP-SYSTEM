package com.schoolerp.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notice_read_receipts")
public class NoticeReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    protected NoticeReadReceipt() {}

    public NoticeReadReceipt(Long noticeId, Long userId) {
        this.noticeId = noticeId;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public Long getNoticeId() { return noticeId; }
    public Long getUserId() { return userId; }
    public Instant getReadAt() { return readAt; }
}

