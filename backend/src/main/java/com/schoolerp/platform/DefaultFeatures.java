package com.schoolerp.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Default feature flags per subscription plan. Flags can be overridden per tenant afterwards. */
public final class DefaultFeatures {

    public static final Set<String> PLANS = Set.of("BASIC", "PRO", "ENTERPRISE");

    private DefaultFeatures() {
    }

    public static Map<String, Boolean> forPlan(String plan) {
        boolean pro = !"BASIC".equals(plan);
        boolean enterprise = "ENTERPRISE".equals(plan);
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("academic", true);
        features.put("staff", true);
        features.put("students", true);
        features.put("attendance", true);
        features.put("fees", true);
        features.put("notices", true);
        features.put("exams", pro);
        features.put("library", pro);
        features.put("transport", pro);
        features.put("hr", enterprise);
        features.put("analytics", enterprise);
        return features;
    }
}
