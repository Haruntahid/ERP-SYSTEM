package com.schoolerp.shared.config;

import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two kinds of data sources exist:
 * - the master data source (platform catalog: tenants table), accessed with masterJdbcTemplate;
 * - the primary, tenant-routed data source used by JPA. It resolves the school database per request.
 */
@Configuration
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource masterDataSource(AppProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.master().url());
        config.setUsername(props.master().username());
        config.setPassword(props.master().password());
        config.setMaximumPoolSize(5);
        config.setPoolName("master");
        HikariDataSource dataSource = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/master")
                .load()
                .migrate();
        return dataSource;
    }

    @Bean
    public JdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return new JdbcTemplate(masterDataSource);
    }

    @Bean
    @Primary
    public TenantRoutingDataSource tenantDataSource(TenantRegistry registry, AppProperties props) {
        return new TenantRoutingDataSource(registry, props.tenancy().poolMaxSize());
    }

    @Bean
    @Primary
    public JdbcTemplate tenantJdbcTemplate(TenantRoutingDataSource tenantDataSource) {
        return new JdbcTemplate(tenantDataSource);
    }
}
