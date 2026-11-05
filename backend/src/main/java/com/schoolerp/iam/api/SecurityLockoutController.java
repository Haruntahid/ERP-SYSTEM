package com.schoolerp.iam.api;

import com.schoolerp.iam.application.AuthService;
import com.schoolerp.iam.application.UserService;
import com.schoolerp.iam.domain.User;
import com.schoolerp.shared.config.OpenApiConfig;
import com.schoolerp.shared.security.AccountLockoutService;
import com.schoolerp.shared.security.AccountLockoutService.AccountLockoutStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Security & Lockouts", description = "Inspect account lockouts, unlock users, and revoke active sessions")
public class SecurityLockoutController {

    private final AccountLockoutService lockoutService;
    private final UserService userService;
    private final AuthService authService;

    public SecurityLockoutController(
            AccountLockoutService lockoutService,
            UserService userService,
            AuthService authService) {
        this.lockoutService = lockoutService;
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping("/lockouts/{userId}")
    @PreAuthorize("@perm.has(authentication, 'security.unlock')")
    @Operation(summary = "Check if a user account is currently locked out (requires `security.unlock`)")
    public AccountLockoutStatusDto getLockoutStatus(@PathVariable long userId) {
        User user = userService.findByIdOrThrow(userId);
        return lockoutService.getStatus(user.getId(), user.getEmail());
    }

    @DeleteMapping("/lockouts/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.has(authentication, 'security.unlock')")
    @Operation(summary = "Clear failed login attempts and unlock a user account (requires `security.unlock`)")
    public void unlockUser(@PathVariable long userId) {
        User user = userService.findByIdOrThrow(userId);
        lockoutService.adminUnlock(user.getId(), user.getEmail());
    }

    @PostMapping("/users/{userId}/revoke-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.has(authentication, 'user.write')")
    @Operation(summary = "Revoke all refresh tokens and immediately invalidate active access tokens for a user (requires `user.write`)")
    public void revokeUserSessions(@PathVariable long userId) {
        authService.revokeAllUserSessions(userId);
    }
}

