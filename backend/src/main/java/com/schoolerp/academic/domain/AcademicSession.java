package com.schoolerp.academic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "academic_sessions")
public class AcademicSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "session_type", nullable = false, length = 20)
    private String sessionType = "YEARLY";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    private String status = "UPCOMING";

    @Column(name = "is_current", nullable = false)
    private boolean current = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AcademicSession() {
    }

    public AcademicSession(String code, String name, String sessionType, LocalDate startDate, LocalDate endDate) {
        this.code = code;
        this.name = name;
        this.sessionType = sessionType == null || sessionType.isBlank() ? "YEARLY" : sessionType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = "UPCOMING";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSessionType() { return sessionType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public boolean isCurrent() { return current; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setCode(String code) { this.code = code; this.updatedAt = Instant.now(); }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; this.updatedAt = Instant.now(); }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; this.updatedAt = Instant.now(); }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; this.updatedAt = Instant.now(); }
    public void activate() { this.status = "ACTIVE"; this.current = true; this.updatedAt = Instant.now(); }
    public void archive() { this.status = "ARCHIVED"; this.current = false; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

