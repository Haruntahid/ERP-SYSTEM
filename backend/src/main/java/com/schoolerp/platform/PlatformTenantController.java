package com.schoolerp.platform;

import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform level API used by the SaaS operator (not by schools). Protected by PlatformKeyFilter. */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@SecurityRequirement(name = OpenApiConfig.PLATFORM_KEY_AUTH)
@Tag(name = "Platform Operator", description = "SaaS operator console APIs for provisioning and managing schools (protected by `X-Platform-Key`)")
public class PlatformTenantController {

    public record CreateTenantRequest(
            @Schema(example = "greenwood", description = "3-24 lowercase letters/digits starting with a letter")
            @NotBlank @Size(max = 24) String slug,
            @Schema(example = "Greenwood High School")
            @NotBlank @Size(max = 200) String name,
            @Schema(example = "PRO", description = "Subscription plan: BASIC, PRO, or ENTERPRISE")
            String plan,
            @Schema(example = "admin@greenwood.school", description = "Initial school admin email (defaults to admin@<slug>.school)")
            @Email @Size(max = 190) String adminEmail,
            @Schema(example = "Greenwood Principal", description = "Initial school admin full name")
            @Size(max = 200) String adminName,
            @Schema(example = "Admin@12345", description = "Initial school admin password (defaults to Admin@12345 in dev)")
            @Size(min = 8, max = 128) String adminPassword) {
    }

    public record UpdateStatusRequest(@NotNull TenantStatus status) {
    }

    public record TenantResponse(String slug, String name, TenantStatus status, String plan,
                                 Map<String, Boolean> features) {
        static TenantResponse from(TenantInfo t) {
            return new TenantResponse(t.slug(), t.name(), t.status(), t.plan(), t.features());
        }
    }

    private final TenantProvisioningService provisioning;
    private final TenantAdminService admin;

    public PlatformTenantController(TenantProvisioningService provisioning, TenantAdminService admin) {
        this.provisioning = provisioning;
        this.admin = admin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Provision a new school database, run migrations, and seed initial SCHOOL_ADMIN",
            description = "Creates database `school<slug>`, DB user `t<slug>`, runs tenant Flyway migrations, creates the initial SCHOOL_ADMIN user, and registers the tenant in `school_master`.")
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        return TenantResponse.from(provisioning.provision(
                request.slug(),
                request.name(),
                request.plan(),
                request.adminEmail(),
                request.adminName(),
                request.adminPassword()));
    }

    @GetMapping
    @Operation(summary = "List all provisioned schools in the platform catalog")
    public List<TenantResponse> list() {
        return admin.list().stream().map(TenantResponse::from).toList();
    }

    @PatchMapping("/{slug}/status")
    @Operation(summary = "Activate or suspend a school")
    public TenantResponse updateStatus(@PathVariable String slug, @Valid @RequestBody UpdateStatusRequest request) {
        return TenantResponse.from(admin.setStatus(slug, request.status()));
    }

    @PutMapping("/{slug}/features")
    @Operation(summary = "Override enabled feature flags for a school")
    public TenantResponse updateFeatures(@PathVariable String slug, @RequestBody Map<String, Boolean> features) {
        return TenantResponse.from(admin.setFeatures(slug, features));
    }
}
