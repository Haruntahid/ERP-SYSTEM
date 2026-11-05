package com.schoolerp.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * SpEL bean ("perm") used in @PreAuthorize expressions to enforce DB-driven permissions and scopes.
 */
@Component("perm")
public class PermissionChecker {

    public boolean has(Authentication authentication, String permissionCode) {
        SchoolUserPrincipal principal = extractPrincipal(authentication);
        return principal != null && principal.hasPermission(permissionCode);
    }

    public boolean hasScoped(
            Authentication authentication,
            String permissionCode,
            String scopeType,
            Long targetId) {
        SchoolUserPrincipal principal = extractPrincipal(authentication);
        if (principal == null || targetId == null) {
            return false;
        }
        ScopeType requiredScope = ScopeType.valueOf(scopeType);
        return principal.hasScopedPermission(permissionCode, requiredScope, targetId);
    }

    public boolean isSelf(Authentication authentication, Long targetUserId) {
        SchoolUserPrincipal principal = extractPrincipal(authentication);
        return principal != null && targetUserId != null && principal.userId() == targetUserId;
    }

    private SchoolUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof SchoolUserPrincipal principal) {
            return principal;
        }
        return null;
    }
}

