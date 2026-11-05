package com.schoolerp.shared.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Masks sensitive JSON fields ({@code password}, {@code otp}, {@code token}, {@code secret})
 * with {@code "***"} before exposing outbox payloads or headers through the admin inspector API.
 */
@Component
public class JsonPayloadMasker {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of("password", "otp", "token", "secret");
    private static final String MASK = "***";

    private final ObjectMapper objectMapper;

    public JsonPayloadMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String maskJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return rawJson;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode masked = maskNode(root);
            return objectMapper.writeValueAsString(masked);
        } catch (Exception e) {
            return rawJson;
        }
    }

    private JsonNode maskNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode obj = ((ObjectNode) node).deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSensitiveKey(entry.getKey())) {
                    entry.setValue(TextNode.valueOf(MASK));
                } else {
                    entry.setValue(maskNode(entry.getValue()));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = ((ArrayNode) node).deepCopy();
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, maskNode(arr.get(i)));
            }
            return arr;
        }
        return node;
    }

    private static boolean isSensitiveKey(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

