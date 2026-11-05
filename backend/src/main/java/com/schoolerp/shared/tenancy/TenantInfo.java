package com.schoolerp.shared.tenancy;

import java.util.Map;

public record TenantInfo(
        long id,
        String slug,
        String name,
        TenantStatus status,
        String plan,
        String jdbcUrl,
        String dbUsername,
        String dbPassword,
        Map<String, Boolean> features) {

    public boolean active() {
        return status == TenantStatus.ACTIVE;
    }

    public boolean featureEnabled(String key) {
        return features != null && Boolean.TRUE.equals(features.get(key));
    }

    @Override
    public String toString() {
        return "TenantInfo[slug=" + slug + ", status=" + status + ", plan=" + plan + "]";
    }
}
