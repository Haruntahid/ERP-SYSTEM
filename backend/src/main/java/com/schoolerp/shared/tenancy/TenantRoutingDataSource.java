package com.schoolerp.shared.tenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * Routes every connection request to the database of the tenant bound to the current thread.
 * Pools are created lazily, one per tenant, and are small by design (database per tenant).
 */
public class TenantRoutingDataSource extends AbstractDataSource implements DisposableBean {

    private final TenantRegistry registry;
    private final int poolMaxSize;
    private final ConcurrentMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public TenantRoutingDataSource(TenantRegistry registry, int poolMaxSize) {
        this.registry = registry;
        this.poolMaxSize = poolMaxSize;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return resolve().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return resolve().getConnection(username, password);
    }

    /** Closes the pool of a tenant, for example after suspension or credential rotation. */
    public void evict(String slug) {
        HikariDataSource pool = pools.remove(slug);
        if (pool != null) {
            pool.close();
        }
    }

    @Override
    public void destroy() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private DataSource resolve() {
        String slug = TenantContext.require();
        return pools.computeIfAbsent(slug, this::createPool);
    }

    private HikariDataSource createPool(String slug) {
        TenantInfo tenant = registry.require(slug);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(tenant.jdbcUrl());
        config.setUsername(tenant.dbUsername());
        config.setPassword(tenant.dbPassword());
        config.setMaximumPoolSize(poolMaxSize);
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setPoolName("tenant-" + slug);
        return new HikariDataSource(config);
    }
}
