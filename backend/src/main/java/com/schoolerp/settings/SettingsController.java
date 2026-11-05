package com.schoolerp.settings;

import com.schoolerp.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per school configuration (branding, locale, grading scale...). Stored in the school's own database. */
@Validated
@RestController
@RequestMapping("/api/v1/settings")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "School Settings", description = "Per-school branding, locale, grading scale, and session settings")
public class SettingsController {

    public record SettingResponse(String key, String value) {
        static SettingResponse from(AppSetting s) {
            return new SettingResponse(s.getKey(), s.getValue());
        }
    }

    public record UpdateSettingRequest(@NotNull @Size(max = 2000) String value) {
    }

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has(authentication, 'settings.read')")
    @Operation(summary = "List all school settings (requires `settings.read`)")
    public List<SettingResponse> list() {
        return service.list().stream().map(SettingResponse::from).toList();
    }

    @PutMapping("/{key}")
    @PreAuthorize("@perm.has(authentication, 'settings.write')")
    @Operation(summary = "Create or update a school setting by key (requires `settings.write`)")
    public SettingResponse update(
            @PathVariable @Pattern(regexp = "^[a-z0-9_.]{1,100}$") String key,
            @Valid @RequestBody UpdateSettingRequest request) {
        return SettingResponse.from(service.upsert(key, request.value()));
    }
}
