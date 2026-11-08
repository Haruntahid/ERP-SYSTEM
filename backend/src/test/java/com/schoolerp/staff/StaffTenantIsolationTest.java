package com.schoolerp.staff;

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

@SpringBootTest
@AutoConfigureMockMvc
class StaffTenantIsolationTest {

    private static final String ALPHA = "alphastaff";
    private static final String BETA = "betastaff";

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
    void tenantIsolationWorksForStaffDirectory() throws Exception {
        provisionTenant(ALPHA, "Alpha Staff School", "admin@alphastaff.school", "Alpha@12345");
        provisionTenant(BETA, "Beta Staff School", "admin@betastaff.school", "Beta@12345");

        String alphaToken = login(ALPHA, "admin@alphastaff.school", "Alpha@12345");
        String betaToken = login(BETA, "admin@betastaff.school", "Beta@12345");

        mockMvc.perform(postStaff(ALPHA, alphaToken, "Rahim", "TEACHING", "EMP-A-001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(postStaff(BETA, betaToken, "Karim", "TEACHING", "EMP-B-001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

        String alphaJson = mockMvc.perform(getStaff(ALPHA, alphaToken)).andReturn().getResponse().getContentAsString();
        String betaJson = mockMvc.perform(getStaff(BETA, betaToken)).andReturn().getResponse().getContentAsString();

        assertThat(alphaJson).contains("Rahim");
        assertThat(alphaJson).doesNotContain("Karim");
        assertThat(betaJson).contains("Karim");
        assertThat(betaJson).doesNotContain("Rahim");
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
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postStaff(String tenant, String token, String firstName, String staffType, String employeeCode) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/staff")
                .header(TenantFilter.TENANT_HEADER, tenant)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "campusId": 1,
                          "departmentId": 1,
                          "designationId": 1,
                          "employeeCode": "%s",
                          "firstName": "%s",
                          "lastName": "Teacher",
                          "email": "%s@school.test",
                          "phone": "01710000000",
                          "staffType": "%s",
                          "joinDate": "2026-01-01"
                        }
                        """.formatted(employeeCode, firstName, firstName.toLowerCase(), staffType));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getStaff(String tenant, String token) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/staff")
                .header(TenantFilter.TENANT_HEADER, tenant)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("page", "0")
                .param("size", "20");
    }
}
