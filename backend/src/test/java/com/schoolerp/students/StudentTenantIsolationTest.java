package com.schoolerp.students;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class StudentTenantIsolationTest {

    private static final String ALPHA = "alphastd";
    private static final String BETA = "betastd";

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
    void tenantIsolationAndAdmissionEnrollmentLifecycleWorks() throws Exception {
        provisionTenant(ALPHA, "Alpha Student School", "admin@alphastd.school", "Alpha@12345");
        provisionTenant(BETA, "Beta Student School", "admin@betastd.school", "Beta@12345");

        String alphaToken = login(ALPHA, "admin@alphastd.school", "Alpha@12345");
        String betaToken = login(BETA, "admin@betastd.school", "Beta@12345");

        setupAcademicStructure(ALPHA, alphaToken);
        setupAcademicStructure(BETA, betaToken);

        String appResp = mockMvc.perform(post("/api/v1/admissions/applications")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "academicSessionId": 1,
                                  "targetCampusId": 1,
                                  "targetClassId": 1,
                                  "targetSectionId": 1,
                                  "applicantFirstName": "Ayesha",
                                  "applicantLastName": "Rahman",
                                  "dateOfBirth": "2014-05-12",
                                  "gender": "FEMALE",
                                  "guardianName": "Mizanur Rahman",
                                  "guardianPhone": "01711111111",
                                  "guardianEmail": "mizan@example.com",
                                  "guardianRelationship": "FATHER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long appId = objectMapper.readTree(appResp).get("id").asLong();

        mockMvc.perform(post("/api/v1/admissions/applications/" + appId + "/status")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED",
                                  "reviewNotes": "Passed admission interview"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admissions/applications/" + appId + "/enroll")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sectionId": 1,
                                  "rollNumber": 1
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/students")
                        .header(TenantFilter.TENANT_HEADER, BETA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Tanvir",
                                  "lastName": "Ahmed",
                                  "dateOfBirth": "2014-08-20",
                                  "gender": "MALE",
                                  "guardianName": "Kamal Ahmed",
                                  "guardianPhone": "01822222222",
                                  "guardianRelationship": "FATHER",
                                  "academicSessionId": 1,
                                  "campusId": 1,
                                  "classId": 1,
                                  "sectionId": 1,
                                  "rollNumber": 1
                                }
                                """))
                .andExpect(status().isCreated());

        String alphaStudents = mockMvc.perform(get("/api/v1/students")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String betaStudents = mockMvc.perform(get("/api/v1/students")
                        .header(TenantFilter.TENANT_HEADER, BETA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(alphaStudents).contains("Ayesha").doesNotContain("Tanvir");
        assertThat(betaStudents).contains("Tanvir").doesNotContain("Ayesha");
    }

    private void setupAcademicStructure(String tenant, String token) throws Exception {
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
                                  "code": "C06",
                                  "name": "Class 6",
                                  "numericLevel": 6
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
