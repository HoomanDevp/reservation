package com.azki.reservation.repository;

import com.azki.reservation.entity.AvailableSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<AvailableSlot, Long> {
    /**
     * Finds the next available time slot using pessimistic locking.
     * Limits the result to only the first available slot to avoid the "Query did not return a unique result" exception.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT t FROM AvailableSlot t WHERE t.isReserved = false AND t.startTime >= :now ORDER BY t.startTime ASC")
    List<AvailableSlot> findAvailableSlots(@Param("now") LocalDateTime now);

    /**
     * Returns the first available time slot, or empty if none exists.
     */
    default Optional<AvailableSlot> findNextAvailable(LocalDateTime now) {
        List<AvailableSlot> slots = findAvailableSlots(now);
        return slots.isEmpty() ? Optional.empty() : Optional.of(slots.getFirst());
    }
}
