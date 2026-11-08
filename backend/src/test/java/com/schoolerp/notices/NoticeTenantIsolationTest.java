package com.schoolerp.notices;

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
class NoticeTenantIsolationTest {

    private static final String ALPHA = "alphanotice";
    private static final String BETA = "betanotice";

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
    void tenantIsolationAndNoticePublishAndReadReceiptWorks() throws Exception {
        provisionTenant(ALPHA, "Alpha Notice School", "admin@alphanotice.school", "Alpha@12345");
        provisionTenant(BETA, "Beta Notice School", "admin@betanotice.school", "Beta@12345");

        String alphaToken = login(ALPHA, "admin@alphanotice.school", "Alpha@12345");
        String betaToken = login(BETA, "admin@betanotice.school", "Beta@12345");

        String createResp = mockMvc.perform(post("/api/v1/notices")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Alpha Annual Sports Day",
                                  "bodyMarkdown": "Annual sports day will be held next Monday.",
                                  "category": "EVENT",
                                  "priority": "IMPORTANT",
                                  "pinned": true,
                                  "sendInApp": true,
                                  "sendEmail": false,
                                  "sendSms": false,
                                  "audiences": [
                                    { "targetType": "WHOLE_SCHOOL" }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long noticeId = objectMapper.readTree(createResp).get("notice").get("id").asLong();

        mockMvc.perform(post("/api/v1/notices/" + noticeId + "/publish")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/notices/" + noticeId + "/read")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isNoContent());

        String alphaFeed = mockMvc.perform(get("/api/v1/notices/feed")
                        .header(TenantFilter.TENANT_HEADER, ALPHA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String betaFeed = mockMvc.perform(get("/api/v1/notices/feed")
                        .header(TenantFilter.TENANT_HEADER, BETA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(alphaFeed).contains("Alpha Annual Sports Day");
        assertThat(betaFeed).doesNotContain("Alpha Annual Sports Day");
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
