package com.schoolerp.shared.security;

public enum RateLimitPolicy {
    AUTH_LOGIN("auth_login"),
    PUBLIC_VERIFY("public_verify"),
    SENSITIVE_MUTATION("sensitive_mutation"),
    TENANT_API_GENERAL("tenant_api");

    private final String code;

    RateLimitPolicy(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}

