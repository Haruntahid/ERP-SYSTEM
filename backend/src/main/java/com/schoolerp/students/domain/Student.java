package com.schoolerp.students.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_code", nullable = false, length = 40, unique = true)
    private String studentCode;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "full_name_bn", length = 200)
    private String fullNameBn;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    private String gender;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(length = 40)
    private String religion;

    @Column(nullable = false, length = 60)
    private String nationality = "Bangladeshi";

    @Column(name = "birth_reg_or_nid", length = 60)
    private String birthRegOrNid;

    @Column(length = 190)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "present_address_json", columnDefinition = "json")
    private String presentAddressJson;

    @Column(name = "permanent_address_json", columnDefinition = "json")
    private String permanentAddressJson;

    @Column(name = "photo_file_id")
    private Long photoFileId;

    @Column(name = "medical_notes", columnDefinition = "text")
    private String medicalNotes;

    @Column(name = "rfid_card_uid", length = 64)
    private String rfidCardUid;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Student() {}

    public Student(
            String studentCode,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            String bloodGroup,
            String email,
            String phone,
            LocalDate admissionDate) {
        this.studentCode = studentCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.bloodGroup = bloodGroup;
        this.email = email;
        this.phone = phone;
        this.admissionDate = admissionDate != null ? admissionDate : LocalDate.now();
    }

    public Long getId() { return id; }
    public String getStudentCode() { return studentCode; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; this.updatedAt = Instant.now(); }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; this.updatedAt = Instant.now(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; this.updatedAt = Instant.now(); }
    public String getFullName() { return (firstName + " " + lastName).trim(); }
    public String getFullNameBn() { return fullNameBn; }
    public void setFullNameBn(String fullNameBn) { this.fullNameBn = fullNameBn; this.updatedAt = Instant.now(); }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; this.updatedAt = Instant.now(); }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; this.updatedAt = Instant.now(); }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; this.updatedAt = Instant.now(); }
    public String getReligion() { return religion; }
    public void setReligion(String religion) { this.religion = religion; this.updatedAt = Instant.now(); }
    public String getNationality() { return nationality; }
    public String getBirthRegOrNid() { return birthRegOrNid; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; this.updatedAt = Instant.now(); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; this.updatedAt = Instant.now(); }
    public String getMedicalNotes() { return medicalNotes; }
    public void setMedicalNotes(String medicalNotes) { this.medicalNotes = medicalNotes; this.updatedAt = Instant.now(); }
    public LocalDate getAdmissionDate() { return admissionDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public Instant getDeletedAt() { return deletedAt; }
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
    }
}

