package com.schoolerp.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schoolerp.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObjectKeyValidatorTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sanitizesPathTraversalAndBuildsTenantPrefixedKey() {
        TenantContext.set("alpha");
        String sanitized = ObjectKeyValidator.sanitizeFilename("../../etc/passwd/../../student report (final).pdf");
        assertEquals("student_report__final_.pdf", sanitized);
        assertFalse(sanitized.contains(".."));
        assertFalse(sanitized.contains("/"));

        String uuid = UUID.randomUUID().toString();
        String key = ObjectKeyValidator.buildObjectKey(
                FileCategory.STUDENT_DOC,
                uuid,
                sanitized,
                LocalDate.of(2026, 11, 5));

        assertTrue(key.startsWith("alpha/STUDENT_DOC/2026/11/" + uuid + "-"));
        ObjectKeyValidator.assertBelongsToCurrentTenant(key);
    }

    @Test
    void rejectsNullBytesAndCrossTenantObjectKeys() {
        assertThrows(SecurityException.class, () -> ObjectKeyValidator.sanitizeFilename("evil\0.jpg"));

        TenantContext.set("alpha");
        String betaKey = "beta/STUDENT_DOC/2026/11/" + UUID.randomUUID() + "-report.pdf";
        assertThrows(SecurityException.class, () -> ObjectKeyValidator.assertBelongsToCurrentTenant(betaKey));

        String traversalKey = "alpha/STUDENT_DOC/2026/11/../../" + UUID.randomUUID() + "-report.pdf";
        assertThrows(SecurityException.class, () -> ObjectKeyValidator.assertBelongsToCurrentTenant(traversalKey));
    }
}

