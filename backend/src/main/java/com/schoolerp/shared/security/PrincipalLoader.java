package com.schoolerp.shared.security;

import java.util.Optional;

/**
 * Cross-cutting spi implemented by the IAM module to load an active user and their server-side
 * roles/permissions from the current tenant database.
 */
public interface PrincipalLoader {

    Optional<SchoolUserPrincipal> loadPrincipal(long userId, String tenantSlug);
}

