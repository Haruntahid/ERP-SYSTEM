package com.schoolerp.shared.security;

/**
 * A single permission code granted to a user within a specific scope.
 */
public record ScopedPermission(String permissionCode, ScopeType scopeType, long scopeId) {
}

