package com.schoolerp.platform;

import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.tenancy.MasterTenantRepository;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantSchemaMigrator;
import com.schoolerp.shared.tenancy.TenantStatus;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Creates a school: dedicated database, dedicated database user, schema migration, initial
 * SCHOOL_ADMIN account, and catalog entry. The master connection must be allowed to create
 * databases and users.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);
    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9]{2,23}$");
    private static final Pattern HOST_PART = Pattern.compile("^[A-Za-z0-9._%-]+$");

    private final JdbcTemplate master;
    private final MasterTenantRepository repository;
    private final TenantSchemaMigrator migrator;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public TenantProvisioningService(
            @Qualifier("masterJdbcTemplate") JdbcTemplate master,
            MasterTenantRepository repository,
            TenantSchemaMigrator migrator,
            PasswordEncoder passwordEncoder,
            AppProperties props) {
        this.master = master;
        this.repository = repository;
        this.migrator = migrator;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    public TenantInfo provision(String slug, String name, String plan) {
        return provision(slug, name, plan, null, null, null);
    }

    public TenantInfo provision(
            String slug,
            String name,
            String plan,
            String adminEmail,
            String adminName,
            String adminPassword) {
        String normalizedPlan = plan == null || plan.isBlank() ? "BASIC" : plan.toUpperCase();
        if (!SLUG.matcher(slug).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Slug must be 3-24 characters: lowercase letters and digits, starting with a letter");
        }
        if (!DefaultFeatures.PLANS.contains(normalizedPlan)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown plan");
        }
        if (repository.findBySlug(slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Tenant already exists");
        }

        String effectiveAdminEmail = (adminEmail == null || adminEmail.isBlank())
                ? "admin@" + slug + ".school"
                : adminEmail.trim().toLowerCase();
        String effectiveAdminName = (adminName == null || adminName.isBlank())
                ? name.trim() + " Admin"
                : adminName.trim();
        String effectiveAdminPassword = (adminPassword == null || adminPassword.isBlank())
                ? "Admin@12345"
                : adminPassword;

        AppProperties.Tenancy cfg = props.tenancy();
        String dbName = "school" + slug;
        String dbUser = "t" + slug;
        String dbPassword = randomPassword();
        String hostPart = cfg.dbUserHost();
        if (cfg.createDbUser() && !HOST_PART.matcher(hostPart).matches()) {
            throw new IllegalStateException("Invalid app.tenancy.db-user-host");
        }
        String jdbcUrl = "jdbc:mysql://" + cfg.dbHost() + ":" + cfg.dbPort() + "/" + dbName + cfg.dbParams();

        boolean databaseCreated = false;
        boolean userCreated = false;
        try {
            master.execute("CREATE DATABASE `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            databaseCreated = true;

            String effectiveUser = props.master().username();
            String effectivePassword = props.master().password();
            if (cfg.createDbUser()) {
                master.execute("CREATE USER '" + dbUser + "'@'" + hostPart + "' IDENTIFIED BY '" + dbPassword + "'");
                userCreated = true;
                master.execute("GRANT ALL PRIVILEGES ON `" + dbName + "`.* TO '" + dbUser + "'@'" + hostPart + "'");
                effectiveUser = dbUser;
                effectivePassword = dbPassword;
            }

            migrator.migrate(jdbcUrl, effectiveUser, effectivePassword);
            seedInitialSchoolData(
                    jdbcUrl,
                    effectiveUser,
                    effectivePassword,
                    name.trim(),
                    effectiveAdminEmail,
                    effectiveAdminName,
                    effectiveAdminPassword);

            repository.insert(slug, name, TenantStatus.ACTIVE, normalizedPlan, jdbcUrl, effectiveUser,
                    effectivePassword, DefaultFeatures.forPlan(normalizedPlan));
        } catch (RuntimeException e) {
            log.error("Provisioning failed for {}, cleaning up", slug, e);
            cleanup(dbName, dbUser, hostPart, databaseCreated, userCreated);
            throw e;
        }
        return repository.findBySlug(slug).orElseThrow();
    }

    private void seedInitialSchoolData(
            String jdbcUrl,
            String dbUser,
            String dbPassword,
            String schoolDisplayName,
            String adminEmail,
            String adminName,
            String adminRawPassword) {
        SingleConnectionDataSource ds = new SingleConnectionDataSource(jdbcUrl, dbUser, dbPassword, true);
        try {
            JdbcTemplate tenantJdbc = new JdbcTemplate(ds);
            tenantJdbc.update(
                    "UPDATE app_settings SET setting_value = ? WHERE setting_key = 'school.display_name'",
                    schoolDisplayName);

            String passwordHash = passwordEncoder.encode(adminRawPassword);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            tenantJdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO users (email, password_hash, full_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, adminEmail);
                ps.setString(2, passwordHash);
                ps.setString(3, adminName);
                return ps;
            }, keyHolder);

            long adminUserId = Objects.requireNonNull(keyHolder.getKey()).longValue();
            tenantJdbc.update(
                    "INSERT INTO user_roles (user_id, role_id, scope_type, scope_id) "
                            + "SELECT ?, id, 'ALL', 0 FROM roles WHERE code = 'SCHOOL_ADMIN'",
                    adminUserId);
        } finally {
            ds.destroy();
        }
    }

    private void cleanup(String dbName, String dbUser, String hostPart, boolean databaseCreated,
                         boolean userCreated) {
        try {
            if (userCreated) {
                master.execute("DROP USER IF EXISTS '" + dbUser + "'@'" + hostPart + "'");
            }
            if (databaseCreated) {
                master.execute("DROP DATABASE IF EXISTS `" + dbName + "`");
            }
        } catch (RuntimeException cleanupError) {
            log.error("Cleanup failed for {}", dbName, cleanupError);
        }
    }

    private String randomPassword() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
