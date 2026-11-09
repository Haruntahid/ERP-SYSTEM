package com.schoolerp.shared.security;

import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 * Authenticated school user principal loaded per request from the current tenant database.
 */
public record SchoolUserPrincipal(
        long userId,
        String email,
        String fullName,
        String tenantSlug,
        Set<String> roles,
        List<ScopedPermission> permissions) implements Principal {

    @Override
    public String getName() {
        return email;
    }

    public boolean isSuperAdmin() {
        if (roles != null && roles.contains("SUPER_ADMIN")) {
            return true;
        }
        return permissions != null
                && permissions.stream()
                        .anyMatch(p -> "*".equals(p.permissionCode()) && p.scopeType() == ScopeType.ALL);
    }

    public boolean hasPermission(String code) {
        if (isSuperAdmin()) {
            return true;
        }
        return permissions.stream()
                .anyMatch(p -> p.permissionCode().equals(code) && p.scopeType() == ScopeType.ALL);
    }

    public boolean hasAnyScopePermission(String code) {
        if (isSuperAdmin()) {
            return true;
        }
        return permissions.stream().anyMatch(p -> p.permissionCode().equals(code));
    }

    public boolean hasScopedPermission(String code, ScopeType requiredScope, long targetId) {
        if (isSuperAdmin()) {
            return true;
        }
        for (ScopedPermission p : permissions) {
            if (!p.permissionCode().equals(code)) {
                continue;
            }
            if (p.scopeType() == ScopeType.ALL) {
                return true;
            }
            if (requiredScope == ScopeType.OWN && p.scopeType() == ScopeType.OWN && this.userId == targetId) {
                return true;
            }
            if (p.scopeType() == requiredScope && p.scopeId() == targetId) {
                return true;
            }
        }
        return false;
    }
}
