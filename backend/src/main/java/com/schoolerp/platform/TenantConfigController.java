package com.schoolerp.platform;

import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the frontend which school it is talking to and which modules are enabled for it. */
@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Tenant Config", description = "Public tenant bootstrap metadata and active feature flags")
public class TenantConfigController {

    public record TenantConfigResponse(String slug, String name, String plan, Map<String, Boolean> features) {
    }

    private final TenantRegistry registry;

    public TenantConfigController(TenantRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/config")
    @SecurityRequirements
    @Operation(summary = "Get active school's display name, plan, and feature flags")
    public TenantConfigResponse config() {
        TenantInfo tenant = registry.require(TenantContext.require());
        return new TenantConfigResponse(tenant.slug(), tenant.name(), tenant.plan(), tenant.features());
    }
}
