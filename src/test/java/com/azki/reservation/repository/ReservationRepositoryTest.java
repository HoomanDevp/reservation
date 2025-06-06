package com.azki.reservation.repository;

import com.azki.reservation.entity.AvailableSlot;
import com.azki.reservation.entity.Reservation;
import com.azki.reservation.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void existsByUserEmailAndStartTimeAfter_shouldReturnTrueWhenFutureReservationExists() {
        // Given
        String email = "test@azki.com";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureTime = now.plusHours(2);

        // Create user
        User user = new User();
        user.setEmail(email);
        user.setUserName("testuser");
        user.setPassword("password");
        entityManager.persist(user);

        // Create available slot
        AvailableSlot slot = new AvailableSlot();
        slot.setStartTime(futureTime);
        slot.setEndTime(futureTime.plusHours(1));
        slot.setReserved(true);
        entityManager.persist(slot);

        // Create reservation
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setAvailableSlot(slot);
        reservation.setReservedAt(now);
        entityManager.persist(reservation);

        entityManager.flush();

        // When
        boolean exists = reservationRepository.existsByUserEmailAndStartTimeAfter(email, now);

        // Then
        assertTrue(exists);
    }

    @Test
    void existsByUserEmailAndStartTimeAfter_shouldReturnFalseWhenNoFutureReservationExists() {
        // Given
        String email = "test@example.com";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastTime = now.minusHours(2);

        // Create user
        User user = new User();
        user.setEmail(email);
        user.setUserName("testuser");
        user.setPassword("password");
        entityManager.persist(user);

        // Create available slot
        AvailableSlot slot = new AvailableSlot();
        slot.setStartTime(pastTime);
        slot.setEndTime(pastTime.plusHours(1));
        slot.setReserved(true);
        entityManager.persist(slot);

        // Create reservation
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setAvailableSlot(slot);
        reservation.setReservedAt(pastTime);
        entityManager.persist(reservation);

        entityManager.flush();

        // When
        boolean exists = reservationRepository.existsByUserEmailAndStartTimeAfter(email, now);

        // Then
        assertFalse(exists);
    }
}
