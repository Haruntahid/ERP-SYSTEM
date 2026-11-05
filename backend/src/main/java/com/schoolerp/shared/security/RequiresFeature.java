package com.schoolerp.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller or endpoint requires the named feature flag to be enabled for the school.
 * Disabled features return 403 Forbidden (rule 01-tenancy.md #11).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

    String value();
}

