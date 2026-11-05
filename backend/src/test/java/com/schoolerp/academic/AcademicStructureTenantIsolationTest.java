package com.schoolerp.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.schoolerp.shared.tenancy.TenantFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void tenantIsolationWorksForAcademicStructure() throws Exception {
        String alpha = "alphacad";
        String beta = "betacad";

        provisionTenant(alpha, "Alpha Academy", "admin@alphacad.school", "Alpha@12345");
        provisionTenant(beta, "Beta Academy", "admin@betacad.school", "Beta@12345");

        String alphaToken = login(alpha, "admin@alphacad.school", "Alpha@12345");
        String betaToken = login(beta, "admin@betacad.school", "Beta@12345");

        mockMvc.perform(postAcademicSession(alpha, alphaToken, "2026", "Academic Year 2026", "YEARLY"));
        mockMvc.perform(postAcademicSession(beta, betaToken, "2026F", "Fall 2026", "SEMESTER"));

        MvcResult alphaList = mockMvc.perform(getAcademicSessions(alpha, alphaToken)).andReturn();
        String alphaJson = alphaList.getResponse().getContentAsString();
        assertThat(alphaJson).contains("Academic Year 2026");
        assertThat(alphaJson).doesNotContain("Fall 2026");

        MvcResult betaList = mockMvc.perform(getAcademicSessions(beta, betaToken)).andReturn();
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

