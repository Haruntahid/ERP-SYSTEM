package com.schoolerp.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "student_documents")
public class StudentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "stored_file_id", nullable = false)
    private Long storedFileId;

    @Column(name = "verified_by_user_id")
    private Long verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected StudentDocument() {}

    public StudentDocument(Long studentId, String documentType, String title, Long storedFileId) {
        this.studentId = studentId;
        this.documentType = documentType;
        this.title = title;
        this.storedFileId = storedFileId;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public String getDocumentType() { return documentType; }
    public String getTitle() { return title; }
    public Long getStoredFileId() { return storedFileId; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

