package com.azki.reservation.service;

import com.azki.reservation.entity.AvailableSlot;
import com.azki.reservation.entity.Reservation;
import com.azki.reservation.entity.User;
import com.azki.reservation.exception.DuplicateReservationException;
import com.azki.reservation.exception.ReservationNotAvailableException;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.TimeSlotRepository;
import com.azki.reservation.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheableOperations cacheableOperations;

    private MeterRegistry meterRegistry;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        reservationService = new ReservationService(
                timeSlotRepository,
                reservationRepository,
                userRepository,
                meterRegistry,
                cacheableOperations
        );
    }

    @Test
    void shouldFindNextAvailableSlot() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        AvailableSlot availableSlot = new AvailableSlot();
        availableSlot.setId(1L);
        availableSlot.setStartTime(now.plusHours(1));
        availableSlot.setEndTime(now.plusHours(2));
        availableSlot.setReserved(false);

        when(timeSlotRepository.findNextAvailable(any(LocalDateTime.class)))
                .thenReturn(Optional.of(availableSlot));

        // When
        Optional<AvailableSlot> result = reservationService.findNextAvailableSlotCached();

        // Then
        assertTrue(result.isPresent());
        assertEquals(availableSlot.getId(), result.get().getId());
        assertEquals(availableSlot.getStartTime(), result.get().getStartTime());
    }

    @Test
    void shouldReserveNearestSlot() {
        // Given
        String email = "test@example.com";
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        AvailableSlot slot = new AvailableSlot();
        slot.setId(1L);
        slot.setStartTime(now.plusHours(1));
        slot.setEndTime(now.plusHours(2));
        slot.setReserved(false);

        Reservation reservation = new Reservation();
        reservation.setId(1L);
        reservation.setUser(user);
        reservation.setAvailableSlot(slot);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findNextAvailable(any(LocalDateTime.class))).thenReturn(Optional.of(slot));
        when(timeSlotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(any(AvailableSlot.class))).thenReturn(slot);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);
        when(reservationRepository.existsByUserEmailAndStartTimeAfter(anyString(), any(LocalDateTime.class))).thenReturn(false);

        // When
        Reservation result = reservationService.reserveNearestSlot(email);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(timeSlotRepository).save(any(AvailableSlot.class));
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void shouldThrowExceptionWhenUserAlreadyHasActiveReservation() {
        // Given
        String email = "test@example.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserEmailAndStartTimeAfter(anyString(), any(LocalDateTime.class))).thenReturn(true);

        // When/Then
        assertThrows(DuplicateReservationException.class, () -> reservationService.reserveNearestSlot(email));
    }

    @Test
    void shouldThrowExceptionWhenNoSlotsAvailable() {
        // Given
        String email = "test@example.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserEmailAndStartTimeAfter(anyString(), any(LocalDateTime.class))).thenReturn(false);
        when(timeSlotRepository.findNextAvailable(any(LocalDateTime.class))).thenReturn(Optional.empty());

        // When/Then
        assertThrows(ReservationNotAvailableException.class, () -> reservationService.reserveNearestSlot(email));
    }

    @Test
    void shouldCancelReservation() {
        // Given
        Long reservationId = 1L;
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");

        AvailableSlot slot = new AvailableSlot();
        slot.setId(1L);
        slot.setStartTime(now.plusHours(1));
        slot.setEndTime(now.plusHours(2));
        slot.setReserved(true);

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setUser(user);
        reservation.setAvailableSlot(slot);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // When
        reservationService.cancelReservation(reservationId);

        // Then
        verify(timeSlotRepository).save(any(AvailableSlot.class));
        verify(reservationRepository).delete(any(Reservation.class));
        assertFalse(slot.isReserved());
    }
}
