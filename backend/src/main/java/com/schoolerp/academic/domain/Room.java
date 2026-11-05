package com.schoolerp.academic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id", nullable = false)
    private Campus campus;

    @Column(name = "room_number", nullable = false, length = 40)
    private String roomNumber;

    @Column(length = 120)
    private String name;

    @Column(name = "room_type", nullable = false, length = 30)
    private String roomType = "CLASSROOM";

    @Column(nullable = false)
    private int capacity = 40;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Room() {}

    public Room(Campus campus, String roomNumber, String name) {
        this.campus = campus;
        this.roomNumber = roomNumber;
        this.name = name;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Campus getCampus() { return campus; }
    public String getRoomNumber() { return roomNumber; }
    public String getName() { return name; }
    public String getRoomType() { return roomType; }
    public int getCapacity() { return capacity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public void setRoomType(String roomType) { this.roomType = roomType; this.updatedAt = Instant.now(); }
    public void setCapacity(int capacity) { this.capacity = capacity; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }
}

