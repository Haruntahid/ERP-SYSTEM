package com.schoolerp.academic.application;

import com.schoolerp.academic.domain.AcademicSession;
import com.schoolerp.academic.infrastructure.AcademicSessionRepository;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicSessionService {

    private final AcademicSessionRepository academicSessionRepository;
    private final AuditService auditService;

    public AcademicSessionService(AcademicSessionRepository academicSessionRepository, AuditService auditService) {
        this.academicSessionRepository = academicSessionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<AcademicSession> findActiveSession() {
        return academicSessionRepository.findByStatusAndDeletedAtIsNull("ACTIVE");
    }

    @Transactional(readOnly = true)
    public AcademicSession getByIdOrThrow(Long id) {
        return academicSessionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Academic session not found"));
    }

    @Transactional
    public AcademicSession create(String code, String name, String sessionType, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session end date must be after the start date");
        }
        if (academicSessionRepository.findByCodeAndDeletedAtIsNull(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Academic session code already exists");
        }
        AcademicSession session = new AcademicSession(code, name, sessionType, startDate, endDate);
        AcademicSession saved = academicSessionRepository.save(session);
        auditService.record("ACADEMIC_SESSION_CREATED", "AcademicSession", String.valueOf(saved.getId()), null,
                Map.of("code", saved.getCode(), "name", saved.getName(), "status", saved.getStatus()));
        return saved;
    }

    @Transactional
    public AcademicSession activate(Long id) {
        AcademicSession session = getByIdOrThrow(id);
        if (session.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Academic session not found");
        }
        findActiveSession().ifPresent(active -> {
            active.archive();
            academicSessionRepository.save(active);
            auditService.record("ACADEMIC_SESSION_ARCHIVED", "AcademicSession", String.valueOf(active.getId()),
                    Map.of("status", "ACTIVE"), Map.of("status", "ARCHIVED"));
        });
        session.activate();
        AcademicSession saved = academicSessionRepository.save(session);
        auditService.record("ACADEMIC_SESSION_ACTIVATED", "AcademicSession", String.valueOf(saved.getId()),
                Map.of("status", saved.getStatus()), Map.of("status", "ACTIVE"));
        return saved;
    }

    @Transactional
    public AcademicSession archive(Long id) {
        AcademicSession session = getByIdOrThrow(id);
        session.archive();
        AcademicSession saved = academicSessionRepository.save(session);
        auditService.record("ACADEMIC_SESSION_ARCHIVED", "AcademicSession", String.valueOf(saved.getId()),
                Map.of("status", "ACTIVE"), Map.of("status", "ARCHIVED"));
        return saved;
    }

    @Transactional(readOnly = true)
    public java.util.List<AcademicSession> listAll() {
        return academicSessionRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null)
                .toList();
    }

    @Transactional
    public AcademicSession update(Long id, String code, String name, String sessionType, LocalDate startDate, LocalDate endDate) {
        AcademicSession session = getByIdOrThrow(id);
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session end date must be after the start date");
        }
        if (code != null && !code.isBlank() && !code.equals(session.getCode())) {
            academicSessionRepository.findByCodeAndDeletedAtIsNull(code.trim()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ApiException(HttpStatus.CONFLICT, "Academic session code already exists");
                }
            });
            session.setCode(code.trim());
        }
        if (name != null && !name.isBlank()) {
            session.setName(name.trim());
        }
        if (sessionType != null && !sessionType.isBlank()) {
            session.setSessionType(sessionType.trim());
        }
        if (startDate != null) {
            session.setStartDate(startDate);
        }
        if (endDate != null) {
            session.setEndDate(endDate);
        }
        return academicSessionRepository.save(session);
    }

    @Transactional
    public void delete(Long id) {
        AcademicSession session = getByIdOrThrow(id);
        session.softDelete();
        academicSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AcademicSession> list(org.springframework.data.domain.Pageable pageable) {
        return academicSessionRepository.findAllByDeletedAtIsNull(pageable);
    }
}
