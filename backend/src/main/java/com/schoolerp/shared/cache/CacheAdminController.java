package com.schoolerp.shared.cache;

import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cache")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Cache Administration", description = "Inspect and flush tenant-scoped Redis caches")
public class CacheAdminController {

    public record EvictCacheRequest(
            @Schema(example = "[\"settings\", \"userPermissions\"]",
                    description = "Cache namespaces to evict (`settings`, `userPermissions`, or `all`)")
            @NotEmpty List<String> cacheNames) {
    }

    public record CacheEvictResultDto(
            List<String> evictedCaches,
            long keysRemoved) {
    }

    private final TenantAwareRedisTemplate redisTemplate;
    private final AuditService auditService;

    public CacheAdminController(TenantAwareRedisTemplate redisTemplate, AuditService auditService) {
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
    }

    @PostMapping("/evict")
    @PreAuthorize("@perm.has(authentication, 'cache.evict')")
    @Operation(summary = "Evict school cache namespaces in Redis (requires `cache.evict`)")
    public CacheEvictResultDto evict(@Valid @RequestBody EvictCacheRequest request) {
        long totalRemoved = 0L;
        for (String name : request.cacheNames()) {
            if ("all".equalsIgnoreCase(name)) {
                totalRemoved += redisTemplate.evictTenantNamespace("settings");
                totalRemoved += redisTemplate.evictTenantNamespace("userPermissions");
            } else if ("settings".equalsIgnoreCase(name) || "userPermissions".equalsIgnoreCase(name)) {
                totalRemoved += redisTemplate.evictTenantNamespace(name);
            }
        }
        auditService.record(
                "TENANT_CACHE_EVICTED",
                "Cache",
                String.join(",", request.cacheNames()),
                null,
                Map.of("cacheNames", request.cacheNames(), "keysRemoved", totalRemoved));
        return new CacheEvictResultDto(request.cacheNames(), totalRemoved);
    }
}

