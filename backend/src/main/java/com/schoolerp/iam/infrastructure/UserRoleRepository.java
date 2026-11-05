package com.schoolerp.iam.infrastructure;

import com.schoolerp.iam.domain.UserRole;
import com.schoolerp.shared.security.ScopeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    Optional<UserRole> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndRole_IdAndScopeTypeAndScopeId(
            Long userId, Long roleId, ScopeType scopeType, long scopeId);
}

