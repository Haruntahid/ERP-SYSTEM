package com.schoolerp.platform;

import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.tenancy.MasterTenantRepository;
import com.schoolerp.shared.tenancy.TenantInfo;
import com.schoolerp.shared.tenancy.TenantRegistry;
import com.schoolerp.shared.tenancy.TenantRoutingDataSource;
import com.schoolerp.shared.tenancy.TenantStatus;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TenantAdminService {

    private final MasterTenantRepository repository;
    private final TenantRegistry registry;
    private final TenantRoutingDataSource routingDataSource;

    public TenantAdminService(MasterTenantRepository repository, TenantRegistry registry,
                              TenantRoutingDataSource routingDataSource) {
        this.repository = repository;
        this.registry = registry;
        this.routingDataSource = routingDataSource;
    }

    public List<TenantInfo> list() {
        return repository.findAll();
    }

    public TenantInfo setStatus(String slug, TenantStatus status) {
        if (repository.updateStatus(slug, status) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown tenant");
        }
        registry.evict(slug);
        if (status != TenantStatus.ACTIVE) {
            routingDataSource.evict(slug);
        }
        return registry.require(slug);
    }

    public TenantInfo setFeatures(String slug, Map<String, Boolean> features) {
        if (repository.updateFeatures(slug, features) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown tenant");
        }
        registry.evict(slug);
        return registry.require(slug);
    }
}
