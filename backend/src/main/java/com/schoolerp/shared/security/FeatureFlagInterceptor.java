package com.schoolerp.shared.security;

import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces @RequiresFeature on tenant endpoints by checking the active school's feature flags.
 */
@Component
public class FeatureFlagInterceptor implements HandlerInterceptor {

    private final TenantRegistry registry;

    public FeatureFlagInterceptor(TenantRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresFeature feature = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequiresFeature.class);
        if (feature == null) {
            feature = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequiresFeature.class);
        }
        if (feature == null) {
            return true;
        }
        String slug = TenantContext.require();
        TenantInfo tenant = registry.require(slug);
        if (!tenant.featureEnabled(feature.value())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Feature '" + feature.value() + "' is not enabled for this school");
        }
        return true;
    }
}

