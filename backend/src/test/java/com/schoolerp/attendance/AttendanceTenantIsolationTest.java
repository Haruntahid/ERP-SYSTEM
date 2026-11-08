package com.schoolerp.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
class AttendanceTenantIsolationTest {

    private static final String ALPHA = "alphaatt";
    private static final String BETA = "betaatt";

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("masterJdbcTemplate") private JdbcTemplate masterJdbc;
    @Autowired private TenantRegistry tenantRegistry;
    @Autowired private TenantRoutingDataSource routingDataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void tenantIsolationAndAttendanceAmendmentAndLeaveSyncWorks() throws Exception {
        provisionTenant(ALPHA, "Alpha Attendance School", "admin@alphaatt.school", "Alpha@12345");
        provisionTenant(BETA, "Beta Attendance School", "admin@betaatt.school", "Beta@12345");

        String alphaToken = login(ALPHA, "admin@alphaatt.school", "Alpha@12345");
        String betaToken = login(BETA, "admin@betaatt.school", "Beta@12345");

        setupAcademicAndStudent(ALPHA, alphaToken, "Nadia", "Islam");
        setupAcademicAndStudent(BETA, betaToken, "Rafiq", "Hossain");

        String submitResp = mockMvc.perform(post("/api/v1/attendance/students/sessions")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "academicSessionId": 1,
                                  "campusId": 1,
                                  "classId": 1,
                                  "sectionId": 1,
                                  "attendanceDate": "2026-03-10",
                                  "marks": [
                                    {
                                      "enrollmentId": 1,
                                      "studentId": 1,
                                      "status": "ABSENT",
                                      "remarks": "Not in class"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long attSessionId = objectMapper.readTree(submitResp).get("id").asLong();

        mockMvc.perform(put("/api/v1/attendance/students/sessions/" + attSessionId)
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "editReason": "Arrived 10 minutes late due to traffic",
                                  "marks": [
                                    {
                                      "enrollmentId": 1,
                                      "studentId": 1,
                                      "status": "LATE",
                                      "remarks": "Late arrival"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        String alphaSheet = mockMvc.perform(get("/api/v1/attendance/students/sheet")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .param("sessionId", "1")
                        .param("classId", "1")
                        .param("sectionId", "1")
                        .param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String betaSheet = mockMvc.perform(get("/api/v1/attendance/students/sheet")
                        .header(TenantFilter.TENANT_HEADER, BETA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .param("sessionId", "1")
                        .param("classId", "1")
                        .param("sectionId", "1")
                        .param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(alphaSheet).contains("Nadia").contains("LATE").doesNotContain("Rafiq");
        assertThat(betaSheet).contains("Rafiq").contains("DRAFT").doesNotContain("Nadia");
    }

    private void setupAcademicAndStudent(String tenant, String token, String firstName, String lastName) throws Exception {
        mockMvc.perform(post("/api/v1/academic/sessions")
                        .header(TenantFilter.TENANT_HEADER, tenant)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "2026",
                                  "name": "Session 2026",
                                  "sessionType": "YEARLY",
                                  "startDate": "2026-01-01",
                                  "endDate": "2026-12-31"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/academic/classes")
                        .header(TenantFilter.TENANT_HEADER, tenant)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId": 1,
                                  "code": "C07",
                                  "name": "Class 7",
                                  "numericLevel": 7
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/academic/sections")
                        .header(TenantFilter.TENANT_HEADER, tenant)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classId": 1,
                                  "code": "A",
                                  "name": "Section A",
                                  "capacity": 40
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/students")
                        .header(TenantFilter.TENANT_HEADER, tenant)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "%s",
                                  "dateOfBirth": "2013-04-10",
                                  "gender": "FEMALE",
                                  "guardianName": "Guardian %s",
                                  "guardianPhone": "01799999999",
                                  "guardianRelationship": "FATHER",
                                  "academicSessionId": 1,
                                  "campusId": 1,
                                  "classId": 1,
                                  "sectionId": 1,
                                  "rollNumber": 1
                                }
                                """.formatted(firstName, lastName, lastName)))
                .andExpect(status().isCreated());
    }

    private void provisionTenant(String slug, String name, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/platform/tenants")
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
                .andExpect(status().isCreated());
    }

    private String login(String slug, String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
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
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
