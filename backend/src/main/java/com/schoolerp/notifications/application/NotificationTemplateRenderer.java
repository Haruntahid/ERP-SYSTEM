package com.schoolerp.notifications.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    public static String render(String template, Map<String, ?> variables, Set<String> allowedVars, boolean htmlEscape) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String name : allowedVars) {
            if (variables.containsKey(name)) {
                normalized.put(name, String.valueOf(variables.get(name)));
            }
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!allowedVars.contains(variable)) {
                throw new IllegalArgumentException("Unsupported template variable: " + variable);
            }
            String value = normalized.getOrDefault(variable, "");
            String resolved = htmlEscape ? escapeHtml(value) : value;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String maskSensitive(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.replaceAll("(?i)(otp|token|resetUrl|code|secret)=?[^\s,;]+", "******")
                .replaceAll("(?i)\\b(otp|token|code|secret)\\b[:=]\\s*[^\\s,;]+", "$1=******")
                .replaceAll("(?i)(https?://[^\s]+)", "******");
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

