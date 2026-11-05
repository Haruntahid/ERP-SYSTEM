package com.schoolerp.iam.api;

import com.schoolerp.iam.application.RoleService;
import com.schoolerp.iam.application.RoleService.RoleView;
import com.schoolerp.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Roles & Permissions", description = "Inspect school roles and their granted permissions")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("@perm.has(authentication, 'role.read')")
    @Operation(summary = "List all roles and their permission codes (requires `role.read`)")
    public List<RoleView> list() {
        return roleService.list();
    }
}

