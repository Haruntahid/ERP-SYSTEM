package com.schoolerp.shared.tenancy;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

/** Applies the tenant schema migrations (classpath:db/tenant) to one school database. */
@Component
public class TenantSchemaMigrator {

    public void migrate(String jdbcUrl, String username, String password) {
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/tenant")
                .load()
                .migrate();
    }

    public void migrate(TenantInfo tenant) {
        migrate(tenant.jdbcUrl(), tenant.dbUsername(), tenant.dbPassword());
    }
}
