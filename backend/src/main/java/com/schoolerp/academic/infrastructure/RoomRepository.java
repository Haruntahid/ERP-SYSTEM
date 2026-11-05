package com.schoolerp.academic.infrastructure;

import com.schoolerp.academic.domain.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByIdAndDeletedAtIsNull(Long id);
    Page<Room> findAllByDeletedAtIsNull(Pageable pageable);
    List<Room> findAllByCampusIdAndDeletedAtIsNull(Long campusId);
    Optional<Room> findByCampusIdAndRoomNumberAndDeletedAtIsNull(Long campusId, String roomNumber);
}

