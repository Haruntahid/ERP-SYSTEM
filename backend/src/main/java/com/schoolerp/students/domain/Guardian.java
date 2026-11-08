package com.schoolerp.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "guardians")
public class Guardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 190)
    private String email;

    @Column(length = 100)
    private String occupation;

    @Column(length = 150)
    private String organization;

    @Column(name = "national_id_no", length = 60)
    private String nationalIdNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Guardian() {}

    public Guardian(String fullName, String phone, String email, String occupation) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.occupation = occupation;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; this.updatedAt = Instant.now(); }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; this.updatedAt = Instant.now(); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; this.updatedAt = Instant.now(); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; this.updatedAt = Instant.now(); }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; this.updatedAt = Instant.now(); }
    public Instant getDeletedAt() { return deletedAt; }
}

