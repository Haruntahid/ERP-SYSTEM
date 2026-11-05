package com.schoolerp.staff.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "designation_id", nullable = false)
    private Long designationId;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 190)
    private String email;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 20)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(name = "national_id", length = 60)
    private String nationalId;

    @Column(length = 255)
    private String qualification;

    @Column(length = 150)
    private String specialization;

    @Column(name = "staff_type", nullable = false, length = 20)
    private String staffType = "TEACHING";

    @Column(name = "employment_status", nullable = false, length = 20)
    private String employmentStatus = "ACTIVE";

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(name = "photo_file_id")
    private Long photoFileId;

    @Column(length = 500)
    private String address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Staff() {}

    public Staff(
            Long campusId,
            Long departmentId,
            Long designationId,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String phone,
            String staffType,
            LocalDate joinDate) {
        this.campusId = campusId;
        this.departmentId = departmentId;
        this.designationId = designationId;
        this.employeeCode = employeeCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = (firstName + " " + lastName).trim();
        this.email = email;
        this.phone = phone;
        this.staffType = staffType == null || staffType.isBlank() ? "TEACHING" : staffType;
        this.joinDate = joinDate;
        this.employmentStatus = "ACTIVE";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getCampusId() { return campusId; }
    public Long getDepartmentId() { return departmentId; }
    public Long getDesignationId() { return designationId; }
    public String getEmployeeCode() { return employeeCode; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getGender() { return gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getBloodGroup() { return bloodGroup; }
    public String getNationalId() { return nationalId; }
    public String getQualification() { return qualification; }
    public String getSpecialization() { return specialization; }
    public String getStaffType() { return staffType; }
    public String getEmploymentStatus() { return employmentStatus; }
    public LocalDate getJoinDate() { return joinDate; }
    public LocalDate getLeaveDate() { return leaveDate; }
    public Long getPhotoFileId() { return photoFileId; }
    public String getAddress() { return address; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setUserId(Long userId) { this.userId = userId; this.updatedAt = Instant.now(); }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; this.updatedAt = Instant.now(); }
    public void setDesignationId(Long designationId) { this.designationId = designationId; this.updatedAt = Instant.now(); }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; this.updatedAt = Instant.now(); }
    public void setFirstName(String firstName) { this.firstName = firstName; this.fullName = (firstName + " " + (lastName == null ? "" : lastName)).trim(); this.updatedAt = Instant.now(); }
    public void setLastName(String lastName) { this.lastName = lastName; this.fullName = ((firstName == null ? "" : firstName) + " " + lastName).trim(); this.updatedAt = Instant.now(); }
    public void setEmail(String email) { this.email = email; this.updatedAt = Instant.now(); }
    public void setPhone(String phone) { this.phone = phone; this.updatedAt = Instant.now(); }
    public void setGender(String gender) { this.gender = gender; this.updatedAt = Instant.now(); }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; this.updatedAt = Instant.now(); }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; this.updatedAt = Instant.now(); }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; this.updatedAt = Instant.now(); }
    public void setQualification(String qualification) { this.qualification = qualification; this.updatedAt = Instant.now(); }
    public void setSpecialization(String specialization) { this.specialization = specialization; this.updatedAt = Instant.now(); }
    public void setStaffType(String staffType) { this.staffType = staffType; this.updatedAt = Instant.now(); }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; this.updatedAt = Instant.now(); }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; this.updatedAt = Instant.now(); }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; this.updatedAt = Instant.now(); }
    public void setPhotoFileId(Long photoFileId) { this.photoFileId = photoFileId; this.updatedAt = Instant.now(); }
    public void setAddress(String address) { this.address = address; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

