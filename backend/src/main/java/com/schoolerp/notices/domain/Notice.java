package com.schoolerp.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notices")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(name = "body_markdown", nullable = false, columnDefinition = "text")
    private String bodyMarkdown;

    @Column(name = "sms_body", length = 500)
    private String smsBody;

    @Column(nullable = false, length = 30)
    private String category = "GENERAL";

    @Column(nullable = false, length = 20)
    private String priority = "NORMAL";

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "is_public", nullable = false)
    private boolean publicNotice;

    @Column(name = "send_in_app", nullable = false)
    private boolean sendInApp = true;

    @Column(name = "send_email", nullable = false)
    private boolean sendEmail;

    @Column(name = "send_sms", nullable = false)
    private boolean sendSms;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "scheduled_publish_at")
    private Instant scheduledPublishAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "total_targeted_count", nullable = false)
    private int totalTargetedCount;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "published_by_user_id")
    private Long publishedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Notice() {}

    public Notice(
            String title,
            String bodyMarkdown,
            String smsBody,
            String category,
            String priority,
            boolean pinned,
            boolean sendInApp,
            boolean sendEmail,
            boolean sendSms,
            Long authorUserId) {
        this.title = title;
        this.bodyMarkdown = bodyMarkdown;
        this.smsBody = smsBody;
        this.category = category != null && !category.isBlank() ? category.toUpperCase() : "GENERAL";
        this.priority = priority != null && !priority.isBlank() ? priority.toUpperCase() : "NORMAL";
        this.pinned = pinned;
        this.sendInApp = sendInApp;
        this.sendEmail = sendEmail;
        this.sendSms = sendSms;
        this.authorUserId = authorUserId != null ? authorUserId : 1L;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; this.updatedAt = Instant.now(); }
    public String getBodyMarkdown() { return bodyMarkdown; }
    public void setBodyMarkdown(String bodyMarkdown) { this.bodyMarkdown = bodyMarkdown; this.updatedAt = Instant.now(); }
    public String getSmsBody() { return smsBody; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public boolean isPinned() { return pinned; }
    public boolean isSendInApp() { return sendInApp; }
    public boolean isSendEmail() { return sendEmail; }
    public boolean isSendSms() { return sendSms; }
    public String getStatus() { return status; }
    public Instant getScheduledPublishAt() { return scheduledPublishAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getTotalTargetedCount() { return totalTargetedCount; }
    public Long getAuthorUserId() { return authorUserId; }
    public Long getPublishedByUserId() { return publishedByUserId; }
    public Instant getCreatedAt() { return createdAt; }

    public void publishNow(Long publisherUserId, int targetedCount) {
        this.status = "PUBLISHED";
        this.publishedByUserId = publisherUserId;
        this.publishedAt = Instant.now();
        this.totalTargetedCount = targetedCount;
        this.updatedAt = Instant.now();
    }

    public void schedule(Instant publishAt) {
        this.status = "SCHEDULED";
        this.scheduledPublishAt = publishAt;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
    }
}

