package com.schoolerp.shared.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.tenancy.TenantFilter;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantRoutingDataSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Multi-tenant isolation and integration test for F-08:
 * 1. Tenant-prefixed cache eviction (evicting alpha does not evict beta).
 * 2. Account lockout after 5 failed login attempts (423 Locked), tenant isolation between alpha and beta,
 *    and immediate admin unlock (DELETE /api/v1/security/lockouts/{userId}).
 * 3. Immediate JWT access token revocation on logout (jti blacklist) and force session revocation
 *    (POST /api/v1/security/users/{userId}/revoke-sessions).
 * 4. TenantDistributedLock mutual exclusion per school.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedisTenantIsolationTest {

    private static final String PLATFORM_KEY = "dev-platform-key";
    private static final String TENANT_A = "cachealpha";
    private static final String TENANT_B = "cachebeta";

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
    private TenantDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        cleanupTenant(TENANT_A);
        cleanupTenant(TENANT_B);
    }

    @AfterEach
    void tearDown() {
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
    void enforcesTenantCacheEvictionAccountLockoutJwtBlacklistAndDistributedLockIsolation() throws Exception {
        // 1. Provision two schools with the same admin email to verify tenant-isolated lockouts
        String sharedEmail = "principal@school.edu";
        provisionTenant(TENANT_A, "Alpha School", sharedEmail, "Alpha@12345");
        provisionTenant(TENANT_B, "Beta School", sharedEmail, "Beta@12345");

        JsonNode tokensA = login(TENANT_A, sharedEmail, "Alpha@12345", "10.10.1.1");
        String accessA = tokensA.get("accessToken").asText();

        JsonNode tokensB = login(TENANT_B, sharedEmail, "Beta@12345", "10.10.2.1");
        String accessB = tokensB.get("accessToken").asText();

        // 2. Populate settings cache in both tenants
        mockMvc.perform(get("/api/v1/settings")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.10.1.1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.10.2.1"))
                .andExpect(status().isOk());

        String alphaSettingsKey = tenantRedisTemplate.tenantKeyForSlug(TENANT_A, "settings", "all");
        String betaSettingsKey = tenantRedisTemplate.tenantKeyForSlug(TENANT_B, "settings", "all");
        assertTrue(tenantRedisTemplate.getRaw(alphaSettingsKey).isPresent());
        assertTrue(tenantRedisTemplate.getRaw(betaSettingsKey).isPresent());

        // 3. Evict cache in Tenant A -> Tenant A cache is cleared while Tenant B cache remains untouched
        mockMvc.perform(post("/api/v1/cache/evict")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.10.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cacheNames\":[\"all\"]}"))
                .andExpect(status().isOk());

        assertFalse(tenantRedisTemplate.getRaw(alphaSettingsKey).isPresent());
        assertTrue(tenantRedisTemplate.getRaw(betaSettingsKey).isPresent());

        // 4. Create a teacher user in Tenant A and trigger 5 failed logins -> account locked (423)
        MvcResult createTeacherRes = mockMvc.perform(post("/api/v1/users")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.10.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "teacher@school.edu",
                                  "fullName": "Teacher Alpha",
                                  "password": "Teacher@12345",
                                  "roleCode": "TEACHER",
                                  "scopeType": "ALL",
                                  "scopeId": 0
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long teacherId = objectMapper.readTree(createTeacherRes.getResponse().getContentAsString())
                .get("id").asLong();

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(TenantFilter.TENANT_HEADER, TENANT_A)
                            .header("X-Forwarded-For", "10.20.0." + attempt)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"teacher@school.edu\",\"password\":\"WrongPass\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // 5th failed attempt triggers lockout (423 Locked)
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header("X-Forwarded-For", "10.20.0.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"teacher@school.edu\",\"password\":\"WrongPass\"}"))
                .andExpect(status().isLocked());

        // 6th attempt even with the CORRECT password still returns 423 Locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header("X-Forwarded-For", "10.20.0.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"teacher@school.edu\",\"password\":\"Teacher@12345\"}"))
                .andExpect(status().isLocked());

        // Verify lockout status endpoint
        mockMvc.perform(get("/api/v1/security/lockouts/" + teacherId)
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.10.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.failedAttempts").value(5));

        // Admin unlocks the teacher account -> login immediately succeeds
        mockMvc.perform(delete("/api/v1/security/lockouts/" + teacherId)
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.10.1.1"))
                .andExpect(status().isNoContent());

        JsonNode teacherTokens = login(TENANT_A, "teacher@school.edu", "Teacher@12345", "10.20.0.7");
        String teacherAccess = teacherTokens.get("accessToken").asText();
        String teacherRefresh = teacherTokens.get("refreshToken").asText();

        // 5. Logout blacklists the current JWT jti immediately
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherAccess)
                        .header("X-Forwarded-For", "10.20.0.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + teacherRefresh + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherAccess)
                        .header("X-Forwarded-For", "10.20.0.7"))
                .andExpect(status().isUnauthorized());

        // 6. Force session revocation via /api/v1/security/users/{id}/revoke-sessions
        JsonNode teacherTokens2 = login(TENANT_A, "teacher@school.edu", "Teacher@12345", "10.20.0.8");
        String teacherAccess2 = teacherTokens2.get("accessToken").asText();

        mockMvc.perform(post("/api/v1/security/users/" + teacherId + "/revoke-sessions")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .header("X-Forwarded-For", "10.10.1.1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherAccess2)
                        .header("X-Forwarded-For", "10.20.0.8"))
                .andExpect(status().isUnauthorized());

        // Tenant B's user with ID 1 is completely unaffected by revocations in Tenant A
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB)
                        .header("X-Forwarded-For", "10.10.2.1"))
                .andExpect(status().isOk());

        // 7. TenantDistributedLock mutual exclusion per tenant
        assertTrue(distributedLock.tryLock(TENANT_A, "outbox:poll", "node-1", Duration.ofSeconds(10)));
        assertFalse(distributedLock.tryLock(TENANT_A, "outbox:poll", "node-2", Duration.ofSeconds(10)));
        assertTrue(distributedLock.tryLock(TENANT_B, "outbox:poll", "node-2", Duration.ofSeconds(10)));
        distributedLock.unlock(TENANT_A, "outbox:poll", "node-1");
        distributedLock.unlock(TENANT_B, "outbox:poll", "node-2");
    }

    private void provisionTenant(String slug, String name, String adminEmail, String adminPassword)
            throws Exception {
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

