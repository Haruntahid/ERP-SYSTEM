package com.schoolerp.notices.application;

import com.schoolerp.notices.domain.Notice;
import com.schoolerp.notices.domain.NoticeAudience;
import com.schoolerp.notices.domain.NoticeReadReceipt;
import com.schoolerp.notices.infrastructure.NoticeAudienceRepository;
import com.schoolerp.notices.infrastructure.NoticeReadReceiptRepository;
import com.schoolerp.notices.infrastructure.NoticeRepository;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.messaging.EventPriority;
import com.schoolerp.shared.messaging.OutboxEventWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeAudienceRepository audienceRepository;
    private final NoticeReadReceiptRepository readReceiptRepository;
    private final AuditService auditService;
    private final OutboxEventWriter outboxEventWriter;

    public NoticeService(
            NoticeRepository noticeRepository,
            NoticeAudienceRepository audienceRepository,
            NoticeReadReceiptRepository readReceiptRepository,
            AuditService auditService,
            OutboxEventWriter outboxEventWriter) {
        this.noticeRepository = noticeRepository;
        this.audienceRepository = audienceRepository;
        this.readReceiptRepository = readReceiptRepository;
        this.auditService = auditService;
        this.outboxEventWriter = outboxEventWriter;
    }

    public record AudienceInput(String targetType, String targetRoleCode, Long targetEntityId) {}

    public record NoticeDetailDto(
            Notice notice,
            List<NoticeAudience> audiences,
            long readCount,
            boolean readByCurrentUser) {}

    @Transactional
    public NoticeDetailDto createNotice(
            String title,
            String bodyMarkdown,
            String smsBody,
            String category,
            String priority,
            boolean pinned,
            boolean sendInApp,
            boolean sendEmail,
            boolean sendSms,
            List<AudienceInput> audiences,
            Long authorUserId) {
        Notice notice = new Notice(
                title.trim(), bodyMarkdown.trim(), smsBody, category, priority, pinned,
                sendInApp, sendEmail, sendSms, authorUserId);
        Notice saved = noticeRepository.save(notice);

        List<NoticeAudience> savedAudiences = new ArrayList<>();
        if (audiences == null || audiences.isEmpty()) {
            savedAudiences.add(audienceRepository.save(new NoticeAudience(saved.getId(), "WHOLE_SCHOOL", null, null)));
        } else {
            for (AudienceInput a : audiences) {
                savedAudiences.add(audienceRepository.save(
                        new NoticeAudience(saved.getId(), a.targetType(), a.targetRoleCode(), a.targetEntityId())));
            }
        }

        auditService.record("NOTICE_CREATED", "Notice", String.valueOf(saved.getId()), null,
                Map.of("title", saved.getTitle(), "category", saved.getCategory()));
        return new NoticeDetailDto(saved, savedAudiences, 0, false);
    }

    @Transactional
    public NoticeDetailDto publishNotice(Long id, Long publisherUserId) {
        Notice notice = getNoticeOrThrow(id);
        if ("PUBLISHED".equals(notice.getStatus())) {
            return getNoticeDetail(id, publisherUserId);
        }
        List<NoticeAudience> audiences = audienceRepository.findAllByNoticeId(id);
        int estimatedTargets = Math.max(1, audiences.size() * 25);
        notice.publishNow(publisherUserId, estimatedTargets);
        Notice saved = noticeRepository.save(notice);

        auditService.record("NOTICE_PUBLISHED", "Notice", String.valueOf(saved.getId()), null,
                Map.of("title", saved.getTitle(), "sendSms", saved.isSendSms(), "targetedCount", estimatedTargets));

        outboxEventWriter.write("Notice", String.valueOf(saved.getId()),
                "NoticePublishedEvent", "notice.published",
                saved.isSendSms() ? EventPriority.HIGH : EventPriority.NORMAL,
                Map.of("noticeId", saved.getId(), "title", saved.getTitle(),
                        "category", saved.getCategory(), "priority", saved.getPriority(),
                        "sendInApp", saved.isSendInApp(), "sendEmail", saved.isSendEmail(),
                        "sendSms", saved.isSendSms()));

        return getNoticeDetail(id, publisherUserId);
    }

    @Transactional
    public Notice archiveNotice(Long id) {
        Notice notice = getNoticeOrThrow(id);
        notice.archive();
        Notice saved = noticeRepository.save(notice);
        auditService.record("NOTICE_ARCHIVED", "Notice", String.valueOf(saved.getId()), null,
                Map.of("title", saved.getTitle()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Notice> listAllNotices(Pageable pageable) {
        return noticeRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Notice> listPublishedFeed(Pageable pageable) {
        return noticeRepository.findAllByStatusAndDeletedAtIsNull("PUBLISHED", pageable);
    }

    @Transactional(readOnly = true)
    public Notice getNoticeOrThrow(Long id) {
        return noticeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notice not found"));
    }

    @Transactional(readOnly = true)
    public NoticeDetailDto getNoticeDetail(Long id, Long currentUserId) {
        Notice notice = getNoticeOrThrow(id);
        List<NoticeAudience> audiences = audienceRepository.findAllByNoticeId(id);
        long readCount = readReceiptRepository.countByNoticeId(id);
        boolean readByMe = currentUserId != null
                && readReceiptRepository.findByNoticeIdAndUserId(id, currentUserId).isPresent();
        return new NoticeDetailDto(notice, audiences, readCount, readByMe);
    }

    @Transactional
    public void markAsRead(Long noticeId, Long userId) {
        getNoticeOrThrow(noticeId);
        if (userId != null && readReceiptRepository.findByNoticeIdAndUserId(noticeId, userId).isEmpty()) {
            readReceiptRepository.save(new NoticeReadReceipt(noticeId, userId));
        }
    }
}

