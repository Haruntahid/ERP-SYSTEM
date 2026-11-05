package com.schoolerp.staff;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.schoolerp.shared.tenancy.TenantFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StaffTenantIsolationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void tenantIsolationWorksForStaffDirectory() throws Exception {
        String alpha = "alphastaff";
        String beta = "betastaff";

        provisionTenant(alpha, "Alpha Staff School", "admin@alphastaff.school", "Alpha@12345");
        provisionTenant(beta, "Beta Staff School", "admin@betastaff.school", "Beta@12345");

        String alphaToken = login(alpha, "admin@alphastaff.school", "Alpha@12345");
        String betaToken = login(beta, "admin@betastaff.school", "Beta@12345");

        mockMvc.perform(postStaff(alpha, alphaToken, "Rahim", "Teacher", "EMP-A-001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(postStaff(beta, betaToken, "Karim", "Teacher", "EMP-B-001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

        String alphaJson = mockMvc.perform(getStaff(alpha, alphaToken)).andReturn().getResponse().getContentAsString();
        String betaJson = mockMvc.perform(getStaff(beta, betaToken)).andReturn().getResponse().getContentAsString();

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

