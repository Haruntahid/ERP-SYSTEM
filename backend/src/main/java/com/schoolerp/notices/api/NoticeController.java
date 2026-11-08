package com.schoolerp.notices.api;

import com.schoolerp.notices.application.NoticeService;
import com.schoolerp.notices.application.NoticeService.AudienceInput;
import com.schoolerp.notices.application.NoticeService.NoticeDetailDto;
import com.schoolerp.notices.domain.Notice;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.RequiresFeature;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/notices")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Notices & Communication", description = "School notice board, targeted broadcasts, and read receipts")
@RequiresFeature("notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    public record CreateNoticeRequest(
            @NotBlank @Size(max = 250) String title,
            @NotBlank String bodyMarkdown,
            @Size(max = 500) String smsBody,
            @Size(max = 30) String category,
            @Size(max = 20) String priority,
            boolean pinned,
            boolean sendInApp,
            boolean sendEmail,
            boolean sendSms,
            List<AudienceInput> audiences) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    @PostMapping
    @PreAuthorize("@perm.has(authentication, 'notice.write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft notice")
    public NoticeDetailDto createNotice(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody CreateNoticeRequest request) {
        Long userId = principal != null ? principal.userId() : 1L;
        return noticeService.createNotice(
                request.title(),
                request.bodyMarkdown(),
                request.smsBody(),
                request.category(),
                request.priority(),
                request.pinned(),
                request.sendInApp(),
                request.sendEmail(),
                request.sendSms(),
                request.audiences(),
                userId);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@perm.has(authentication, 'notice.publish')")
    @Operation(summary = "Publish a notice and dispatch notifications")
    public NoticeDetailDto publishNotice(
            @PathVariable Long id,
            @AuthenticationPrincipal SchoolUserPrincipal principal) {
        Long userId = principal != null ? principal.userId() : 1L;
        return noticeService.publishNotice(id, userId);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@perm.has(authentication, 'notice.write')")
    @Operation(summary = "Archive a notice")
    public Notice archiveNotice(@PathVariable Long id) {
        return noticeService.archiveNotice(id);
    }

    @GetMapping
    @PreAuthorize("@perm.has(authentication, 'notice.manage.read')")
    @Operation(summary = "List all notices for management")
    public PageResponse<Notice> listNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        Page<Notice> result = noticeService.listAllNotices(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/feed")
    @PreAuthorize("@perm.has(authentication, 'notice.feed.read')")
    @Operation(summary = "List published notices feed")
    public PageResponse<Notice> listFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("id")));
        Page<Notice> result = noticeService.listPublishedFeed(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has(authentication, 'notice.feed.read')")
    @Operation(summary = "Get notice detail and read statistics")
    public NoticeDetailDto getNotice(
            @PathVariable Long id,
            @AuthenticationPrincipal SchoolUserPrincipal principal) {
        Long userId = principal != null ? principal.userId() : null;
        return noticeService.getNoticeDetail(id, userId);
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@perm.has(authentication, 'notice.feed.read')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark notice as read by current user")
    public void markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal SchoolUserPrincipal principal) {
        Long userId = principal != null ? principal.userId() : 1L;
        noticeService.markAsRead(id, userId);
    }
}

