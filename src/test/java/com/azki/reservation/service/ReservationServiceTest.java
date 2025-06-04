package com.azki.reservation.service;

import com.azki.reservation.entity.AvailableSlot;
import com.azki.reservation.entity.Reservation;
import com.azki.reservation.entity.User;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.TimeSlotRepository;
import com.azki.reservation.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    @Mock TimeSlotRepository timeSlotRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock UserRepository userRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks ReservationService reservationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reservationService = new ReservationService(timeSlotRepository, reservationRepository, userRepository, cacheManager, meterRegistry);
    }

    @Test
    void reserveNearestSlot_success() {
        User user = new User(); user.setEmail("test@example.com");
        AvailableSlot slot = new AvailableSlot(); slot.setId(1L); slot.setReserved(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(timeSlotRepository.findNextAvailable(any())).thenReturn(Optional.of(slot));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(any())).thenReturn(slot);
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Reservation reservation = reservationService.reserveNearestSlot("test@example.com");
        assertNotNull(reservation);
        assertEquals(user, reservation.getUser());
        assertEquals(slot, reservation.getAvailableSlot());
    }

    @Test
    void reserveNearestSlot_userNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> reservationService.reserveNearestSlot("notfound@example.com"));
    }

    @Test
    void reserveNearestSlot_noAvailableSlot() {
        User user = new User(); user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(timeSlotRepository.findNextAvailable(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> reservationService.reserveNearestSlot("test@example.com"));
    }

    @Test
    void reserveNearestSlot_optimisticLockingFailure() {
        User user = new User(); user.setEmail("test@example.com");
        AvailableSlot slot = new AvailableSlot(); slot.setId(1L); slot.setReserved(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(timeSlotRepository.findNextAvailable(any())).thenReturn(Optional.of(slot));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(any())).thenThrow(new OptimisticLockingFailureException("Optimistic lock"));
        assertThrows(IllegalStateException.class, () -> reservationService.reserveNearestSlot("test@example.com"));
    }

    @Test
    void cancelReservation_success() {
        Reservation reservation = new Reservation();
        AvailableSlot slot = new AvailableSlot(); slot.setId(1L); slot.setReserved(true);
        reservation.setAvailableSlot(slot);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(timeSlotRepository.save(any())).thenReturn(slot);
        doNothing().when(reservationRepository).delete(any());
        when(cacheManager.getCache(any())).thenReturn(cache);
        doNothing().when(cache).evict(any());
        assertDoesNotThrow(() -> reservationService.cancelReservation(1L));
    }
}

