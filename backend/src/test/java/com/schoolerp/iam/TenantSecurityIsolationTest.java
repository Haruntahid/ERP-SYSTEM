package com.schoolerp.iam;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.tenancy.TenantFilter;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantRoutingDataSource;
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
 * End-to-end integration and multi-tenant isolation test verifying:
 * 1. Swagger / OpenAPI specification generation (/v3/api-docs).
 * 2. Provisioning two isolated schools (alpha & beta) with initial SCHOOL_ADMIN accounts.
 * 3. JWT authentication, /api/v1/auth/me, and rotating refresh token reuse detection.
 * 4. Cross-tenant token rejection (Tenant A's JWT rejected with 403 on Tenant B).
 * 5. Physical data isolation (users and settings in Tenant A are invisible in Tenant B).
 * 6. RBAC permission enforcement (STUDENT rejected with 403 on settings.write).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantSecurityIsolationTest {

    private static final String PLATFORM_KEY = "dev-platform-key";
    private static final String TENANT_A = "testalpha";
    private static final String TENANT_B = "testbeta";

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
        routingDataSource.evict(slug);
        tenantRegistry.evict(slug);
        masterJdbc.update("DELETE FROM tenants WHERE slug = ?", slug);
        masterJdbc.execute("DROP DATABASE IF EXISTS `school" + slug + "`");
        masterJdbc.execute("DROP USER IF EXISTS 't" + slug + "'@'%'");
    }

    @Test
    void swaggerOpenApiSpecIsExposedWithSecuritySchemes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.PlatformKeyAuth.name").value("X-Platform-Key"));
    }

    @Test
    void enforcesCrossTenantJwtRejectionPhysicalDataIsolationAndRbac() throws Exception {
        // 1. Provision Tenant A and Tenant B
        provisionTenant(TENANT_A, "Alpha Academy", "admin@testalpha.school", "Alpha@12345");
        provisionTenant(TENANT_B, "Beta College", "admin@testbeta.school", "Beta@12345");

        // 2. Unauthenticated call to protected endpoint returns 401
        mockMvc.perform(get("/api/v1/settings")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A))
                .andExpect(status().isUnauthorized());

        // 3. Login as Tenant A Admin and Tenant B Admin
        JsonNode tokensA = login(TENANT_A, "admin@testalpha.school", "Alpha@12345");
        String accessA = tokensA.get("accessToken").asText();
        String refreshA = tokensA.get("refreshToken").asText();

        JsonNode tokensB = login(TENANT_B, "admin@testbeta.school", "Beta@12345");
        String accessB = tokensB.get("accessToken").asText();

        // 4. Verify /api/v1/auth/me for Tenant A Admin
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@testalpha.school"))
                .andExpect(jsonPath("$.tenant").value(TENANT_A))
                .andExpect(jsonPath("$.roles", hasItem("SCHOOL_ADMIN")));

        // 5. Cross-tenant token check: using Tenant A's JWT with X-Tenant-ID: testbeta MUST return 403
        mockMvc.perform(get("/api/v1/settings")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Token tenant claim does not match")));

        // 6. Physical isolation check: create a user in Tenant A, verify not visible in Tenant B
        mockMvc.perform(post("/api/v1/users")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student1@testalpha.school",
                                  "fullName": "Alpha Student",
                                  "password": "Student@12345",
                                  "roleCode": "STUDENT",
                                  "scopeType": "OWN",
                                  "scopeId": 0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("student1@testalpha.school"));

        mockMvc.perform(get("/api/v1/users")
                        .header(TenantFilter.TENANT_HEADER, TENANT_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email", not(hasItem("student1@testalpha.school"))));

        // 7. RBAC check: login as STUDENT in Tenant A and verify PUT /api/v1/settings is forbidden (403)
        JsonNode studentTokens = login(TENANT_A, "student1@testalpha.school", "Student@12345");
        String studentAccess = studentTokens.get("accessToken").asText();

        mockMvc.perform(put("/api/v1/settings/brand.primary_color")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"#ff0000\"}"))
                .andExpect(status().isForbidden());

        // Admin in Tenant A CAN update the setting
        mockMvc.perform(put("/api/v1/settings/brand.primary_color")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"#059669\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("#059669"));

        // 8. Refresh token rotation & reuse detection
        MvcResult rotatedResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshA + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String secondRefreshA = objectMapper.readTree(rotatedResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Replaying the old refreshA token must fail with 401 AND revoke secondRefreshA
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshA + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(TenantFilter.TENANT_HEADER, TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefreshA + "\"}"))
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug));
    }

    private JsonNode login(String slug, String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .header(TenantFilter.TENANT_HEADER, slug)
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

