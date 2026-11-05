package com.schoolerp.shared.cache;

import com.schoolerp.shared.tenancy.TenantContext;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/**
 * Generates tenant-isolated cache keys in the format {@code <tenantSlug>:<cacheName>:<key>}
 * (rule 01-tenancy.md #6, F-08 BR-01). Throws {@link IllegalStateException} if invoked without
 * an active {@link TenantContext}.
 */
@Component("tenantPrefixedCacheKeyGenerator")
public class TenantPrefixedCacheKeyGenerator implements KeyGenerator {

    public String buildTenantKey(String namespace, Object... params) {
        String tenantSlug = TenantContext.get()
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot generate tenant cache key without an active TenantContext"));
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Cache namespace must not be blank");
        }
        String suffix = formatParams(params);
        return tenantSlug + ":" + namespace + ":" + suffix;
    }

    public String buildPlatformKey(String namespace, Object... params) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Cache namespace must not be blank");
        }
        return "platform:" + namespace + ":" + formatParams(params);
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return buildTenantKey(method.getName(), params);
    }

    private String formatParams(Object... params) {
        if (params == null || params.length == 0) {
            return "all";
        }
        if (params.length == 1) {
            return params[0] == null ? "null" : String.valueOf(params[0]);
        }
        StringJoiner joiner = new StringJoiner(":");
        for (Object param : params) {
            joiner.add(param == null ? "null" : String.valueOf(param));
        }
        return joiner.toString();
    }
}

