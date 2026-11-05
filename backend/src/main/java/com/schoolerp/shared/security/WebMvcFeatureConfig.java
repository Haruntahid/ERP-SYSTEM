package com.schoolerp.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcFeatureConfig implements WebMvcConfigurer {

    private final FeatureFlagInterceptor featureFlagInterceptor;

    public WebMvcFeatureConfig(FeatureFlagInterceptor featureFlagInterceptor) {
        this.featureFlagInterceptor = featureFlagInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(featureFlagInterceptor).addPathPatterns("/api/v1/**");
    }
}

