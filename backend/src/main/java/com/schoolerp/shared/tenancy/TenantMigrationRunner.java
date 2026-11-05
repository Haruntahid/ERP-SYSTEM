package com.schoolerp.shared.tenancy;

import com.schoolerp.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Brings every existing school database to the latest schema at startup. A failing tenant is logged and
 * skipped so one broken school cannot block the others. Replace with a rolling or parallel strategy once
 * the number of tenants grows.
 */
@Component
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final MasterTenantRepository repository;
    private final TenantSchemaMigrator migrator;
    private final AppProperties props;

    public TenantMigrationRunner(MasterTenantRepository repository, TenantSchemaMigrator migrator,
                                 AppProperties props) {
        this.repository = repository;
        this.migrator = migrator;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.tenancy().migrateOnStartup()) {
            return;
        }
        for (TenantInfo tenant : repository.findAll()) {
            try {
                migrator.migrate(tenant);
                log.info("Tenant schema up to date: {}", tenant.slug());
            } catch (RuntimeException e) {
                log.error("Tenant schema migration failed: {}", tenant.slug(), e);
            }
        }
    }
}
