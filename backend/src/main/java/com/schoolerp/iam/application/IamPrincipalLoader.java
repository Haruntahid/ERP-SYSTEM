package com.schoolerp.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.iam.domain.Permission;
import com.schoolerp.iam.domain.User;
import com.schoolerp.iam.domain.UserRole;
import com.schoolerp.iam.infrastructure.UserRepository;
import com.schoolerp.iam.infrastructure.UserRoleRepository;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.security.PrincipalLoader;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import com.schoolerp.shared.security.ScopedPermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamPrincipalLoader implements PrincipalLoader {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TenantAwareRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration permissionsTtl;

    @Autowired
    public IamPrincipalLoader(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            TenantAwareRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AppProperties props) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        long ttlSeconds = props != null && props.cache() != null
                ? props.cache().permissionsTtlSeconds()
                : 300L;
        this.permissionsTtl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolUserPrincipal> loadPrincipal(long userId, String tenantSlug) {
        String cacheKey = redisTemplate.tenantKeyForSlug(tenantSlug, "userPermissions", userId);
        Optional<String> cachedJson = redisTemplate.getRaw(cacheKey);
        if (cachedJson.isPresent()) {
            try {
                SchoolUserPrincipal cached = objectMapper.readValue(
                        cachedJson.get(), SchoolUserPrincipal.class);
                if (tenantSlug.equals(cached.tenantSlug())) {
                    return Optional.of(cached);
                }
            } catch (Exception ignored) {
                // Fall back to DB lookup
            }
        }

        Optional<User> userOpt = userRepository.findByIdAndDeletedAtIsNull(userId);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return Optional.empty();
        }
        User user = userOpt.get();
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);

        Set<String> roleCodes = new LinkedHashSet<>();
        List<ScopedPermission> permissions = new ArrayList<>();
        for (UserRole ur : userRoles) {
            roleCodes.add(ur.getRole().getCode());
            for (Permission perm : ur.getRole().getPermissions()) {
                permissions.add(new ScopedPermission(
                        perm.getCode(), ur.getScopeType(), ur.getScopeId()));
            }
        }

        SchoolUserPrincipal principal = new SchoolUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                tenantSlug,
                Set.copyOf(roleCodes),
                List.copyOf(permissions));
        try {
            redisTemplate.setRaw(
                    cacheKey, objectMapper.writeValueAsString(principal), permissionsTtl);
        } catch (Exception ignored) {
            // Non-fatal
        }
        return Optional.of(principal);
    }
}
