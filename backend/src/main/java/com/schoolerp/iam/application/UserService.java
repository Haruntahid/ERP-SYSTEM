package com.schoolerp.iam.application;

import com.schoolerp.iam.domain.Role;
import com.schoolerp.iam.domain.User;
import com.schoolerp.iam.domain.UserRole;
import com.schoolerp.iam.infrastructure.RoleRepository;
import com.schoolerp.iam.infrastructure.UserRepository;
import com.schoolerp.iam.infrastructure.UserRoleRepository;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.cache.UserPermissionsChangedInternalEvent;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.security.ScopeType;
import com.schoolerp.shared.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    public record RoleAssignmentView(Long id, String roleCode, String roleName, ScopeType scopeType, long scopeId) {
        static RoleAssignmentView from(UserRole ur) {
            return new RoleAssignmentView(
                    ur.getId(),
                    ur.getRole().getCode(),
                    ur.getRole().getName(),
                    ur.getScopeType(),
                    ur.getScopeId());
        }
    }

    public record UserView(
            Long id,
            String email,
            String fullName,
            String status,
            Instant createdAt,
            List<RoleAssignmentView> roles) {
    }

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final TenantAwareRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            TenantAwareRedisTemplate redisTemplate,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) {
        return userRepository.findAllByDeletedAtIsNull(pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public User findByIdOrThrow(long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public UserView create(
            String email,
            String fullName,
            String rawPassword,
            String roleCode,
            ScopeType scopeType,
            long scopeId) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        User user = userRepository.save(new User(
                normalizedEmail, passwordEncoder.encode(rawPassword), fullName));

        if (roleCode != null && !roleCode.isBlank()) {
            Role role = roleRepository.findByCode(roleCode.trim().toUpperCase())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown role code: " + roleCode));
            ScopeType effectiveScope = scopeType != null ? scopeType : ScopeType.ALL;
            long effectiveScopeId = effectiveScope == ScopeType.ALL ? 0L : scopeId;
            userRoleRepository.save(new UserRole(user.getId(), role, effectiveScope, effectiveScopeId));
        }

        auditService.record(
                "USER_CREATED",
                "User",
                String.valueOf(user.getId()),
                null,
                Map.of("email", user.getEmail(), "fullName", user.getFullName()));
        return toView(user);
    }

    @Transactional
    public UserView assignRole(long userId, String roleCode, ScopeType scopeType, long scopeId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        Role role = roleRepository.findByCode(roleCode.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown role code: " + roleCode));

        ScopeType effectiveScope = scopeType != null ? scopeType : ScopeType.ALL;
        long effectiveScopeId = effectiveScope == ScopeType.ALL ? 0L : scopeId;

        if (userRoleRepository.existsByUserIdAndRole_IdAndScopeTypeAndScopeId(
                userId, role.getId(), effectiveScope, effectiveScopeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Role with this scope is already assigned to user");
        }

        UserRole saved = userRoleRepository.save(
                new UserRole(userId, role, effectiveScope, effectiveScopeId));
        auditService.record(
                "USER_ROLE_ASSIGNED",
                "UserRole",
                String.valueOf(saved.getId()),
                null,
                Map.of(
                        "userId", userId,
                        "roleCode", role.getCode(),
                        "scopeType", effectiveScope.name(),
                        "scopeId", effectiveScopeId));

        TenantContext.get().ifPresent(tenantSlug -> {
            redisTemplate.deleteTenantKey("userPermissions", userId);
            eventPublisher.publishEvent(new UserPermissionsChangedInternalEvent(tenantSlug, userId, false));
        });
        return toView(user);
    }

    @Transactional
    public void revokeRole(long userId, long userRoleId) {
        UserRole ur = userRoleRepository.findByIdAndUserId(userRoleId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Role assignment not found"));
        Map<String, Object> oldState = Map.of(
                "userId", userId,
                "roleCode", ur.getRole().getCode(),
                "scopeType", ur.getScopeType().name(),
                "scopeId", ur.getScopeId());
        userRoleRepository.delete(ur);
        auditService.record(
                "USER_ROLE_REVOKED",
                "UserRole",
                String.valueOf(userRoleId),
                oldState,
                null);

        TenantContext.get().ifPresent(tenantSlug -> {
            redisTemplate.deleteTenantKey("userPermissions", userId);
            eventPublisher.publishEvent(new UserPermissionsChangedInternalEvent(tenantSlug, userId, false));
        });
    }

    private UserView toView(User user) {
        List<RoleAssignmentView> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(RoleAssignmentView::from)
                .toList();
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                user.getCreatedAt(),
                roles);
    }
}
