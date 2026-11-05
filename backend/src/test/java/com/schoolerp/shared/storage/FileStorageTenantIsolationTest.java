package com.schoolerp.shared.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.tenancy.TenantContext;
import com.schoolerp.shared.tenancy.TenantFilter;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantRoutingDataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full two-school integration and tenant isolation test for F-06:
 * 1. Uploads valid PDF under filealpha -> verifies objectKey starts with filealpha/STUDENT_DOC/...
 * 2. Verifies filebeta cannot read metadata, request download URL, or delete filealpha's file (404).
 * 3. Verifies StorageProvider throws SecurityException if invoked with filealpha's objectKey while TenantContext is filebeta.
 * 4. Verifies LocalFsStorageProvider HMAC stream token works for filealpha with nosniff/CSP headers,
 *    and is rejected with 403 Forbidden if replayed with X-Tenant-ID: filebeta.
 * 5. Verifies polyglot/disguised executable and EICAR malware uploads are rejected with 422 Unprocessable Entity.
 * 6. Verifies per-school storage quota enforcement (422 STORAGE_QUOTA_EXCEEDED) and soft-delete lifecycle.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileStorageTenantIsolationTest {

    private static final String PLATFORM_KEY = "dev-platform-key";
    private static final String TENANT_A = "filealpha";
    private static final String TENANT_B = "filebeta";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbc;

    @Autowired
    private TenantRegistry tenantRegistry;

    @Autowired
    private TenantRoutingDataSource routingDataSource;

    @Autowired
    private TenantAwareRedisTemplate tenantRedisTemplate;

    @Autowired
    private StorageProvider storageProvider;

    @BeforeEach
    void setUp() {
        cleanupTenant(TENANT_A);
        cleanupTenant(TENANT_B);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanupTenant(TENANT_A);
        cleanupTenant(TENANT_B);
    }

    private void cleanupTenant(String slug) {
        tenantRedisTemplate.evictTenantNamespaceForSlug(slug, "all");
        routingDataSource.evict(slug);
        tenantRegistry.evict(slug);
        masterJdbc.update("DELETE FROM tenants WHERE slug = ?", slug);
        masterJdbc.execute("DROP DATABASE IF EXISTS `school" + slug + "`");
        masterJdbc.execute("DROP USER IF EXISTS 't" + slug + "'@'%'");
    }

    @Test
    void enforcesTwoSchoolFileIsolationMagicByteMalwareScanQuotaAndSoftDelete() throws Exception {
        provisionTenant(TENANT_A, "Alpha Files School", "admin@filealpha.edu", "Alpha@12345");
        provisionTenant(TENANT_B, "Beta Files School", "admin@filebeta.edu", "Beta@12345");

        String accessA = login(TENANT_A, "admin@filealpha.edu", "Alpha@12345", "10.30.1.1").get("accessToken").asText();
        String accessB = login(TENANT_B, "admin@filebeta.edu", "Beta@12345", "10.30.2.1").get("accessToken").asText();

        // 1. Upload valid PDF in TENANT_A
        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "Birth Certificate #1.pdf", "application/pdf", pdfBytes);

        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(pdfFile)
                        .param("category", "STUDENT_DOC")
                        .param("entityType", "STUDENT")
                        .param("entityId", "1001")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("STUDENT_DOC"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.status").value("ATTACHED"))
                .andReturn();

        JsonNode uploadedNode = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
        String alphaFileUuid = uploadedNode.get("fileUuid").asText();
        String alphaObjectKey = uploadedNode.get("objectKey").asText();
        assertTrue(alphaObjectKey.startsWith(TENANT_A + "/STUDENT_DOC/"));

        // 2. TENANT_B cannot view metadata, request download URL, or delete TENANT_A's file
        mockMvc.perform(get("/api/v1/files/" + alphaFileUuid)
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.30.2.1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/files/" + alphaFileUuid + "/download-url")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.30.2.1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/files/" + alphaFileUuid)
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.30.2.1"))
                .andExpect(status().isNotFound());

        // 3. Direct StorageProvider invocation with TENANT_A key while TenantContext is TENANT_B throws SecurityException
        try {
            TenantContext.set(TENANT_B);
            assertThrows(
                    SecurityException.class,
                    () -> storageProvider.generatePresignedGetUrl(
                            alphaObjectKey, "doc.pdf", "application/pdf", "attachment", Duration.ofSeconds(60)));
        } finally {
            TenantContext.clear();
        }

        // 4. TENANT_A requests pre-signed download URL and streams the file
        MvcResult dlUrlRes = mockMvc.perform(get("/api/v1/files/" + alphaFileUuid + "/download-url")
                        .param("disposition", "inline")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isOk())
                .andReturn();
        String streamUrl = objectMapper.readTree(dlUrlRes.getResponse().getContentAsString()).get("url").asText();
        assertTrue(streamUrl.startsWith("/api/v1/files/stream/"));

        MvcResult streamRes = mockMvc.perform(get(streamUrl)
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andReturn();
        assertArrayEquals(pdfBytes, streamRes.getResponse().getContentAsByteArray());

        // Replaying TENANT_A's signed stream URL with X-Tenant-ID: TENANT_B is rejected with 403 Forbidden
        mockMvc.perform(get(streamUrl)
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header("X-Forwarded-For", "10.30.2.1"))
                .andExpect(status().isForbidden());

        // 5. Reject disguised executable and EICAR test virus with 422 Unprocessable Entity
        MockMultipartFile disguisedExe = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "MZ\u0090\u0000Executable".getBytes(StandardCharsets.ISO_8859_1));
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(disguisedExe)
                        .param("category", "PROFILE_PHOTO")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isUnprocessableEntity());

        byte[] eicarPdf = ("%PDF-1.7\nX5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile eicarFile = new MockMultipartFile(
                "file", "infected.pdf", "application/pdf", eicarPdf);
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(eicarFile)
                        .param("category", "STUDENT_DOC")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isUnprocessableEntity());

        // 6. Verify quota endpoint and soft-delete lifecycle
        mockMvc.perform(get("/api/v1/files/quota")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedBytes").value(pdfBytes.length))
                .andExpect(jsonPath("$.fileCountByCategory.STUDENT_DOC").value(1));

        // Soft-delete alphaFileUuid -> 204 No Content, subsequent GET -> 404 Not Found
        mockMvc.perform(delete("/api/v1/files/" + alphaFileUuid)
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/files/" + alphaFileUuid)
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isNotFound());

        // Quota usedBytes drops back to 0 after soft delete
        mockMvc.perform(get("/api/v1/files/quota")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedBytes").value(0));

        // Set TENANT_A storage.max_quota_mb = 1 and upload > 1MB PDF -> 422 STORAGE_QUOTA_EXCEEDED
        mockMvc.perform(put("/api/v1/settings/storage.max_quota_mb")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"1\"}"))
                .andExpect(status().isOk());

        byte[] largePdf = new byte[1024 * 1024 + 512];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, largePdf, 0, header.length);
        MockMultipartFile overQuotaFile = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", largePdf);

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(overQuotaFile)
                        .param("category", "STUDENT_DOC")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.30.1.1"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void provisionTenant(String slug, String name, String adminEmail, String adminPassword) throws Exception {
        mockMvc.perform(post("/api/v1/platform/tenants")
                        .header("X-Platform-Key", PLATFORM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "%s",
                                  "name": "%s",
                                  "plan": "PRO",
                                  "adminEmail": "%s",
                                  "adminName": "%s Admin",
                                  "adminPassword": "%s"
                                }
                                """.formatted(slug, name, adminEmail, name, adminPassword)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String slug, String email, String password, String clientIp) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header(TenantFilter.TENANT_HEADER, slug)
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}

