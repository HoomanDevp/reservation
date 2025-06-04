package com.azki.reservation.repository;

import com.azki.reservation.entity.AvailableSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<AvailableSlot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AvailableSlot t WHERE t.isReserved = false AND t.startTime >= :now ORDER BY t.startTime ASC")
    Optional<AvailableSlot> findNextAvailable(@Param("now") LocalDateTime now);
}
