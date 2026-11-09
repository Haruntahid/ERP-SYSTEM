package com.schoolerp.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.cache.SettingsUpdatedInternalEvent;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.tenancy.TenantContext;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private final AppSettingRepository repository;
    private final AuditService auditService;
    private final TenantAwareRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration settingsCacheTtl;

    @Autowired
    public SettingsService(
            AppSettingRepository repository,
            AuditService auditService,
            TenantAwareRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            AppProperties props) {
        this.repository = repository;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        long ttlSeconds = props != null && props.cache() != null
                ? props.cache().settingsTtlSeconds()
                : 600L;
        this.settingsCacheTtl = Duration.ofSeconds(ttlSeconds);
    }

    @Transactional(readOnly = true)
    public List<AppSetting> list() {
        Map<String, String> cachedMap = getCachedSettingsMap();
        return cachedMap.entrySet().stream()
                .map(e -> new AppSetting(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, String> getCachedSettingsMap() {
        if (TenantContext.get().isEmpty()) {
            return loadFromDb();
        }
        Optional<String> cachedJson = redisTemplate.getTenantValue("settings", "all");
        if (cachedJson.isPresent()) {
            try {
                return objectMapper.readValue(cachedJson.get(), MAP_TYPE);
            } catch (Exception ignored) {
                // Fall back to DB reload
            }
        }
        Map<String, String> map = loadFromDb();
        try {
            redisTemplate.setTenantValue(
                    "settings", settingsCacheTtl, objectMapper.writeValueAsString(map), "all");
        } catch (Exception ignored) {
            // Non-fatal
        }
        return map;
    }

    @Transactional(readOnly = true)
    public String getString(String key, String defaultValue) {
        try {
            return getCachedSettingsMap().getOrDefault(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        try {
            String raw = getCachedSettingsMap().get(key);
            return raw != null ? Integer.parseInt(raw.trim()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional
    public AppSetting upsert(String key, String value) {
        AppSetting setting = repository.findById(key).orElse(null);
        String oldValue = setting != null ? setting.getValue() : null;
        AppSetting saved;
        if (setting == null) {
            saved = repository.save(new AppSetting(key, value));
        } else {
            setting.changeValue(value);
            saved = setting;
        }
        auditService.record(
                "SETTING_UPDATED",
                "AppSetting",
                key,
                oldValue != null ? Map.of("value", oldValue) : null,
                Map.of("value", value));

        TenantContext.get().ifPresent(tenantSlug -> {
            redisTemplate.deleteTenantKey("settings", "all");
            eventPublisher.publishEvent(new SettingsUpdatedInternalEvent(tenantSlug, key));
        });
        return saved;
    }

    @Transactional
    public Map<String, String> bulkImport(Map<String, String> incoming) {
        if (incoming != null) {
            for (Map.Entry<String, String> entry : incoming.entrySet()) {
                String k = entry.getKey() == null ? "" : entry.getKey().trim();
                String v = entry.getValue() == null ? "" : entry.getValue();
                if (!k.isEmpty() && k.matches("^[a-z0-9_.]{1,100}$")) {
                    AppSetting existing = repository.findById(k).orElse(null);
                    if (existing == null) {
                        repository.save(new AppSetting(k, v));
                    } else {
                        existing.changeValue(v);
                        repository.save(existing);
                    }
                }
            }
            TenantContext.get().ifPresent(tenantSlug -> {
                redisTemplate.deleteTenantKey("settings", "all");
                eventPublisher.publishEvent(new SettingsUpdatedInternalEvent(tenantSlug, "*"));
            });
        }
        return loadFromDb();
    }

    @Transactional
    public void deleteKey(String key) {
        if (repository.existsById(key)) {
            repository.deleteById(key);
            TenantContext.get().ifPresent(tenantSlug -> {
                redisTemplate.deleteTenantKey("settings", "all");
                eventPublisher.publishEvent(new SettingsUpdatedInternalEvent(tenantSlug, key));
            });
        }
    }

    private Map<String, String> loadFromDb() {
        Map<String, String> map = new LinkedHashMap<>();
        for (AppSetting s : repository.findAll()) {
            map.put(s.getKey(), s.getValue());
        }
        return map;
    }
}
