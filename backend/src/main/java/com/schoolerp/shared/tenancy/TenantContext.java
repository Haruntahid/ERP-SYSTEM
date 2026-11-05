package com.schoolerp.shared.tenancy;

import java.util.Optional;

/**
 * Holds the tenant slug for the current thread. Always set by TenantFilter and cleared in a finally block.
 * Background jobs and async tasks must bind the tenant explicitly before touching tenant data.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String slug) {
        CURRENT.set(slug);
    }

    public static Optional<String> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static String require() {
        String slug = CURRENT.get();
        if (slug == null) {
            throw new IllegalStateException("No tenant is bound to the current thread");
        }
        return slug;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
