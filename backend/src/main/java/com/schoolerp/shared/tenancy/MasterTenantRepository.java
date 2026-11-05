package com.schoolerp.shared.tenancy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.crypto.CryptoService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Access to the platform catalog of tenants in the master database. */
@Repository
public class MasterTenantRepository {

    private static final String SELECT =
            "SELECT id, slug, name, status, plan, jdbc_url, db_username, db_password_enc, features_json FROM tenants";

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final ObjectMapper objectMapper;

    public MasterTenantRepository(
            @Qualifier("masterJdbcTemplate") JdbcTemplate jdbc, CryptoService crypto, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
    }

    public Optional<TenantInfo> findBySlug(String slug) {
        List<TenantInfo> rows = jdbc.query(SELECT + " WHERE slug = ?", this::map, slug);
        return rows.stream().findFirst();
    }

    public List<TenantInfo> findAll() {
        return jdbc.query(SELECT + " ORDER BY id", this::map);
    }

    public void insert(String slug, String name, TenantStatus status, String plan, String jdbcUrl,
                       String dbUsername, String dbPassword, Map<String, Boolean> features) {
        jdbc.update(
                "INSERT INTO tenants (slug, name, status, plan, jdbc_url, db_username, db_password_enc, features_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                slug, name, status.name(), plan, jdbcUrl, dbUsername, crypto.encrypt(dbPassword),
                writeFeatures(features));
    }

    public int updateStatus(String slug, TenantStatus status) {
        return jdbc.update("UPDATE tenants SET status = ? WHERE slug = ?", status.name(), slug);
    }

    public int updateFeatures(String slug, Map<String, Boolean> features) {
        return jdbc.update("UPDATE tenants SET features_json = ? WHERE slug = ?", writeFeatures(features), slug);
    }

    private TenantInfo map(ResultSet rs, int rowNum) throws SQLException {
        return new TenantInfo(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("name"),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getString("plan"),
                rs.getString("jdbc_url"),
                rs.getString("db_username"),
                crypto.decrypt(rs.getString("db_password_enc")),
                readFeatures(rs.getString("features_json")));
    }

    private String writeFeatures(Map<String, Boolean> features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize features", e);
        }
    }

    private Map<String, Boolean> readFeatures(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot parse features", e);
        }
    }
}
