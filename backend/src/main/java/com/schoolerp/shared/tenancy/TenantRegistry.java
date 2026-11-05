package com.schoolerp.shared.tenancy;

import com.schoolerp.shared.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves tenant metadata from the master database with a short in-memory cache. */
@Component
public class TenantRegistry {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Cached(TenantInfo info, Instant loadedAt) {
        boolean expired() {
            return loadedAt.plus(TTL).isBefore(Instant.now());
        }
    }

    private final MasterTenantRepository repository;
    private final ConcurrentMap<String, Cached> cache = new ConcurrentHashMap<>();

    public TenantRegistry(MasterTenantRepository repository) {
        this.repository = repository;
    }

    public Optional<TenantInfo> find(String slug) {
        Cached cached = cache.get(slug);
        if (cached != null && !cached.expired()) {
            return Optional.of(cached.info());
        }
        Optional<TenantInfo> loaded = repository.findBySlug(slug);
        if (loaded.isPresent()) {
            cache.put(slug, new Cached(loaded.get(), Instant.now()));
        } else {
            cache.remove(slug);
        }
        return loaded;
    }

    public TenantInfo require(String slug) {
        return find(slug).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown tenant"));
    }

    public void evict(String slug) {
        cache.remove(slug);
    }
}
