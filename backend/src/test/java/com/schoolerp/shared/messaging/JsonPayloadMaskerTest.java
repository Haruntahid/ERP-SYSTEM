package com.schoolerp.shared.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonPayloadMaskerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonPayloadMasker masker = new JsonPayloadMasker(objectMapper);

    @Test
    void masksPasswordOtpTokenAndSecretFieldsRecursively() throws Exception {
        String rawJson = """
                {
                  "eventId": "evt-123",
                  "tenantSlug": "school-a",
                  "payload": {
                    "email": "student@school-a.edu",
                    "temporaryPassword": "PlainPassword123!",
                    "otpCode": "849201",
                    "nested": {
                      "resetToken": "tok_live_secret_value",
                      "webhookSecret": "whsec_999",
                      "displayName": "Rahim Uddin"
                    }
                  }
                }
                """;

        String maskedJson = masker.maskJson(rawJson);
        JsonNode root = objectMapper.readTree(maskedJson);
        JsonNode payload = root.get("payload");

        assertEquals("student@school-a.edu", payload.get("email").asText());
        assertEquals("***", payload.get("temporaryPassword").asText());
        assertEquals("***", payload.get("otpCode").asText());
        assertEquals("***", payload.get("nested").get("resetToken").asText());
        assertEquals("***", payload.get("nested").get("webhookSecret").asText());
        assertEquals("Rahim Uddin", payload.get("nested").get("displayName").asText());
        assertTrue(!maskedJson.contains("PlainPassword123!"));
        assertTrue(!maskedJson.contains("849201"));
        assertTrue(!maskedJson.contains("tok_live_secret_value"));
    }
}

