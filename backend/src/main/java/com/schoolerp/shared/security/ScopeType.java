package com.schoolerp.shared.security;

/**
 * Authorization scope levels from broadest (ALL) to narrowest (OWN).
 */
public enum ScopeType {
    ALL,
    DEPARTMENT,
    CLASS,
    SECTION,
    OWN
}

