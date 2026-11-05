package com.schoolerp.shared.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schoolerp.shared.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantPrefixedCacheKeyGeneratorTest {

    private final TenantPrefixedCacheKeyGenerator generator = new TenantPrefixedCacheKeyGenerator();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void prefixesKeysWithCurrentTenantSlug() {
        TenantContext.set("alpha");
        assertEquals("alpha:settings:all", generator.buildTenantKey("settings"));
        assertEquals("alpha:userPermissions:42", generator.buildTenantKey("userPermissions", 42L));

        TenantContext.set("beta");
        assertEquals("beta:settings:all", generator.buildTenantKey("settings"));
        assertEquals("beta:userPermissions:42", generator.buildTenantKey("userPermissions", 42L));
    }

    @Test
    void throwsIllegalStateExceptionWhenTenantContextIsMissing() {
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> generator.buildTenantKey("settings"));
    }

    @Test
    void buildsPlatformKeyWithPlatformPrefix() {
        assertEquals("platform:tenant:alpha", generator.buildPlatformKey("tenant", "alpha"));
    }
}

