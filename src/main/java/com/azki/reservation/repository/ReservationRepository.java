package com.azki.reservation.repository;

import com.azki.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Checks if a user with the given email has any reservations starting after the specified time.
     * Useful for preventing duplicate active reservations.
     *
     * @param email the user's email
     * @param dateTime the date and time to check from (usually current time)
     * @return true if the user has future reservations, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Reservation r " +
           "JOIN r.user u JOIN r.availableSlot a " +
           "WHERE u.email = :email AND a.startTime > :dateTime")
    boolean existsByUserEmailAndStartTimeAfter(@Param("email") String email, @Param("dateTime") LocalDateTime dateTime);
}
