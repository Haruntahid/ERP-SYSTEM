package com.schoolerp.iam.application;

import com.schoolerp.iam.domain.Permission;
import com.schoolerp.iam.domain.Role;
import com.schoolerp.iam.infrastructure.RoleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    public record RoleView(Long id, String code, String name, boolean systemRole, List<String> permissions) {
        static RoleView from(Role role) {
            List<String> perms = role.getPermissions().stream()
                    .map(Permission::getCode)
                    .sorted()
                    .toList();
            return new RoleView(role.getId(), role.getCode(), role.getName(), role.isSystemRole(), perms);
        }
    }

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        return roleRepository.findAll().stream().map(RoleView::from).toList();
    }
}

