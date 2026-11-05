package com.schoolerp.iam.api;

import com.schoolerp.iam.application.AuthService;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import com.schoolerp.shared.security.ScopedPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Tenant login, rotating refresh tokens, logout, and current user identity")
public class AuthController {

    public record LoginRequest(
            @Schema(example = "admin@greenwood.school")
            @NotBlank @Email @Size(max = 190) String email,
            @Schema(example = "Admin@12345")
            @NotBlank @Size(min = 6, max = 128) String password) {
    }

    public record RefreshRequest(
            @NotBlank @Size(max = 256) String refreshToken) {
    }

    public record LogoutRequest(
            @NotBlank @Size(max = 256) String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(min = 6, max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds) {
        static TokenResponse from(AuthService.TokenPair pair) {
            return new TokenResponse(
                    pair.accessToken(),
                    pair.refreshToken(),
                    "Bearer",
                    pair.expiresInSeconds());
        }
    }

    public record CurrentUserResponse(
            long id,
            String email,
            String fullName,
            String tenant,
            Set<String> roles,
            List<ScopedPermission> permissions) {
        static CurrentUserResponse from(SchoolUserPrincipal p) {
            return new CurrentUserResponse(
                    p.userId(),
                    p.email(),
                    p.fullName(),
                    p.tenantSlug(),
                    p.roles(),
                    p.permissions());
        }
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Authenticate with email and password",
            description = "Issues a short-lived JWT access token (with tenant claim) and a rotating refresh token.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Rotate refresh token and issue new access token",
            description = "Revokes the presented refresh token and issues a new access + refresh token pair.")
    @ApiResponse(responseCode = "200", description = "Tokens rotated")
    @ApiResponse(responseCode = "401", description = "Invalid, expired, or reused refresh token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Revoke a refresh token and blacklist the active access token on logout")
    public void logout(
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = org.springframework.http.HttpHeaders.AUTHORIZATION,
                    required = false) String authHeader,
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken(), authHeader);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(
            summary = "Get current authenticated user profile, roles, and scoped permissions",
            description = "Returns the identity and server-side RBAC permissions loaded for the current school.")
    public CurrentUserResponse me(@AuthenticationPrincipal SchoolUserPrincipal principal) {
        return CurrentUserResponse.from(principal);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Change current user's password and revoke active refresh tokens")
    public void changePassword(
            @AuthenticationPrincipal SchoolUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(
                principal.userId(), request.currentPassword(), request.newPassword());
    }
}

