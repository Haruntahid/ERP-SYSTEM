package com.schoolerp.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

@SpringBootTest
@AutoConfigureMockMvc
class AcademicStructureTenantIsolationTest {

    private static final String ALPHA = "alphacad";
    private static final String BETA = "betacad";

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("masterJdbcTemplate") private JdbcTemplate masterJdbc;
    @Autowired private TenantRegistry tenantRegistry;
    @Autowired private TenantRoutingDataSource routingDataSource;

    @BeforeEach
    void setUp() {
        cleanupTenant(ALPHA);
        cleanupTenant(BETA);
    }

    @AfterEach
    void tearDown() {
        cleanupTenant(ALPHA);
        cleanupTenant(BETA);
    }

    private void cleanupTenant(String slug) {
        routingDataSource.evict(slug);
        tenantRegistry.evict(slug);
        masterJdbc.update("DELETE FROM tenants WHERE slug = ?", slug);
        masterJdbc.execute("DROP DATABASE IF EXISTS `school" + slug + "`");
        masterJdbc.execute("DROP USER IF EXISTS 't" + slug + "'@'%'");
    }

    @Test
    void tenantIsolationWorksForAcademicStructure() throws Exception {
        provisionTenant(ALPHA, "Alpha Academy", "admin@alphacad.school", "Alpha@12345");
        provisionTenant(BETA, "Beta Academy", "admin@betacad.school", "Beta@12345");

        String alphaToken = login(ALPHA, "admin@alphacad.school", "Alpha@12345");
        String betaToken = login(BETA, "admin@betacad.school", "Beta@12345");

        mockMvc.perform(postAcademicSession(ALPHA, alphaToken, "2026", "Academic Year 2026", "YEARLY"));
        mockMvc.perform(postAcademicSession(BETA, betaToken, "2026F", "Fall 2026", "SEMESTER"));

        MvcResult alphaList = mockMvc.perform(getAcademicSessions(ALPHA, alphaToken)).andReturn();
        String alphaJson = alphaList.getResponse().getContentAsString();
        assertThat(alphaJson).contains("Academic Year 2026");
        assertThat(alphaJson).doesNotContain("Fall 2026");

        MvcResult betaList = mockMvc.perform(getAcademicSessions(BETA, betaToken)).andReturn();
        String betaJson = betaList.getResponse().getContentAsString();
        assertThat(betaJson).contains("Fall 2026");
        assertThat(betaJson).doesNotContain("Academic Year 2026");
    }

    private void provisionTenant(String slug, String name, String email, String password) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/platform/tenants")
                        .header("X-Platform-Key", "dev-platform-key")
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
                                """.formatted(slug, name, email, name, password)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    private String login(String slug, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .header(TenantFilter.TENANT_HEADER, slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAcademicSession(String tenant, String token, String code, String name, String type) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/academic/sessions")
                .header(TenantFilter.TENANT_HEADER, tenant)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code": "%s",
                          "name": "%s",
                          "sessionType": "%s",
                          "startDate": "2026-01-01",
                          "endDate": "2026-12-31"
                        }
                        """.formatted(code, name, type));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAcademicSessions(String tenant, String token) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/academic/sessions")
                .header(TenantFilter.TENANT_HEADER, tenant)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("page", "0")
                .param("size", "20");
    }
}
