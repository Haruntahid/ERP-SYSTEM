package com.schoolerp.shared.cache;

public record UserPermissionsChangedInternalEvent(String tenantSlug, long userId, boolean revokeSessions) {
}

