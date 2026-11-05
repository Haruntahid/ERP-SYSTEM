package com.schoolerp.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Append-only audit writer recording sensitive actions into the current school's audit_log table.
 */
@Service
public class AuditService {

    private final JdbcTemplate tenantJdbc;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate tenantJdbc, ObjectMapper objectMapper) {
        this.tenantJdbc = tenantJdbc;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String entityType, String entityId, Object oldValue, Object newValue) {
        recordAs(currentUserId(), action, entityType, entityId, oldValue, newValue);
    }

    public void recordAs(
            Long actorUserId,
            String action,
            String entityType,
            String entityId,
            Object oldValue,
            Object newValue) {
        tenantJdbc.update(
                "INSERT INTO audit_log (actor_user_id, action, entity_type, entity_id, old_value, new_value) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                actorUserId,
                action,
                entityType,
                entityId,
                toJson(oldValue),
                toJson(newValue));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SchoolUserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }
}

