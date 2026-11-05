package com.schoolerp.iam.api;

import com.schoolerp.iam.application.UserService;
import com.schoolerp.iam.application.UserService.UserView;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.ScopeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Users & Access", description = "Manage school users and scoped role assignments")
public class UserController {

    public record PagedUsersResponse(
            List<UserView> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        static PagedUsersResponse from(Page<UserView> p) {
            return new PagedUsersResponse(
                    p.getContent(),
                    p.getNumber(),
                    p.getSize(),
                    p.getTotalElements(),
                    p.getTotalPages());
        }
    }

    public record CreateUserRequest(
            @Schema(example = "teacher1@greenwood.school")
            @NotBlank @Email @Size(max = 190) String email,
            @Schema(example = "Rahim Uddin")
            @NotBlank @Size(max = 200) String fullName,
            @Schema(example = "Teacher@123")
            @NotBlank @Size(min = 8, max = 128) String password,
            @Schema(example = "TEACHER", description = "Optional initial role code")
            String roleCode,
            @Schema(example = "SECTION", description = "Scope for the initial role (ALL, DEPARTMENT, CLASS, SECTION, OWN)")
            ScopeType scopeType,
            @Schema(example = "101", description = "Target scope entity ID (0 when scopeType is ALL)")
            long scopeId) {
    }

    public record AssignRoleRequest(
            @Schema(example = "TEACHER")
            @NotBlank @Size(max = 60) String roleCode,
            @Schema(example = "SECTION")
            ScopeType scopeType,
            @Schema(example = "101")
            long scopeId) {
    }

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("@perm.has(authentication, 'user.read')")
    @Operation(summary = "List users in the current school (paginated, requires `user.read`)")
    public PagedUsersResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return PagedUsersResponse.from(userService.list(pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(authentication, 'user.write')")
    @Operation(summary = "Create a new user in the current school (requires `user.write`)")
    public UserView create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(
                request.email(),
                request.fullName(),
                request.password(),
                request.roleCode(),
                request.scopeType(),
                request.scopeId());
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@perm.has(authentication, 'role.write')")
    @Operation(summary = "Assign a scoped role to a user (requires `role.write`)")
    public UserView assignRole(
            @PathVariable long id,
            @Valid @RequestBody AssignRoleRequest request) {
        return userService.assignRole(id, request.roleCode(), request.scopeType(), request.scopeId());
    }

    @DeleteMapping("/{id}/roles/{userRoleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.has(authentication, 'role.write')")
    @Operation(summary = "Revoke a scoped role assignment from a user (requires `role.write`)")
    public void revokeRole(@PathVariable long id, @PathVariable long userRoleId) {
        userService.revokeRole(id, userRoleId);
    }
}

