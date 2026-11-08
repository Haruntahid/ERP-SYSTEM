package com.schoolerp.fees;

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
class FeeTenantIsolationTest {

    private static final String ALPHA = "alphafee";
    private static final String BETA = "betafee";

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
    void tenantIsolationAndInvoicePaymentAllocationAndLedgerInvariantsWork() throws Exception {
        provisionTenant(ALPHA, "Alpha Fee School", "admin@alphafee.school", "Alpha@12345");
        provisionTenant(BETA, "Beta Fee School", "admin@betafee.school", "Beta@12345");

        String alphaToken = login(ALPHA, "admin@alphafee.school", "Alpha@12345");
        String betaToken = login(BETA, "admin@betafee.school", "Beta@12345");

        setupAcademicAndStudent(ALPHA, alphaToken, "Farhan", "Kabir");
        setupAcademicAndStudent(BETA, betaToken, "Sadia", "Akter");

        String invResp = mockMvc.perform(post("/api/v1/fees/invoices")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enrollmentId": 1,
                                  "studentId": 1,
                                  "academicSessionId": 1,
                                  "billingMonth": "2026-03",
                                  "dueDate": "2026-03-15",
                                  "lines": [
                                    {
                                      "feeHeadId": 3,
                                      "description": "March Tuition",
                                      "grossAmount": 2000.00,
                                      "discountAmount": 0.00,
                                      "fineAmount": 0.00
                                    },
                                    {
                                      "feeHeadId": 1,
                                      "description": "Late Fine",
                                      "grossAmount": 200.00,
                                      "discountAmount": 0.00,
                                      "fineAmount": 0.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long invoiceId = objectMapper.readTree(invResp).get("invoice").get("id").asLong();

        mockMvc.perform(post("/api/v1/fees/payments/offline")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .header("Idempotency-Key", "idem-alpha-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": %d,
                                  "amount": 1000.00,
                                  "gatewayCode": "OFFLINE_CASH"
                                }
                                """.formatted(invoiceId)))
                .andExpect(status().isCreated());

        String invDetail = mockMvc.perform(get("/api/v1/fees/invoices/" + invoiceId)
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode invNode = objectMapper.readTree(invDetail).get("invoice");
        assertThat(invNode.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(invNode.get("balanceDueAmount").decimalValue()).isEqualByComparingTo("1200.00");

        String alphaLedger = mockMvc.perform(get("/api/v1/fees/students/1/ledger")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String betaInvoices = mockMvc.perform(get("/api/v1/fees/invoices")
                        .header(TenantFilter.TENANT_HEADER, BETA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(alphaLedger).contains("INVOICE_ISSUED").contains("PAYMENT_RECEIVED");
        assertThat(betaInvoices).doesNotContain("2026-03");
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
                                  "code": "C08",
                                  "name": "Class 8",
                                  "numericLevel": 8
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
                                  "dateOfBirth": "2012-09-15",
                                  "gender": "MALE",
                                  "guardianName": "Guardian %s",
                                  "guardianPhone": "01788888888",
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
