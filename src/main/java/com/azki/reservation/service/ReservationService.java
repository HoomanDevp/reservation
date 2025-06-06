package com.azki.reservation.service;

import com.azki.reservation.entity.Reservation;
import com.azki.reservation.entity.AvailableSlot;
import com.azki.reservation.entity.User;
import com.azki.reservation.exception.BusinessException;
import com.azki.reservation.exception.DuplicateReservationException;
import com.azki.reservation.exception.ReservationCapacityExceededException;
import com.azki.reservation.exception.ReservationNotAvailableException;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.TimeSlotRepository;
import com.azki.reservation.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for handling reservation operations such as reserving, cancelling, and finding available slots.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    private final CacheableOperations cacheableOperations;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    /**
     * Finds and caches the next available time slot.
     * This method delegates to the CacheableOperations interface to ensure proper caching.
     *
     * @return Optional containing the next available TimeSlot, or empty if none found.
     */
    public Optional<AvailableSlot> findNextAvailableSlotCached() {
        return cacheableOperations.findNextAvailableSlotCached(LocalDateTime.now());
    }

    /**
     * Reserves the nearest available time slot for the given user email with optimistic locking
     * to handle concurrency. Retries up to MAX_RETRY_ATTEMPTS times if reservation fails due to
     * concurrent modification by another transaction.
     *
     * @param email the user's email
     * @return the created Reservation
     * @throws BusinessException if the user is not found or no available time slots exist
     */
    @Retryable(
        value = OptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 10, multiplier = 1.5)
    )
    @Transactional
    public Reservation reserveNearestSlot(String email) {
        logger.info("Attempting to reserve nearest slot for user: {}", email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        logger.warn("User not found for email: {}", email);
                        return new BusinessException("User not found for email: " + email);
                    });
            logger.debug("Found user: id={}, email={}", user.getId(), user.getEmail());

            // Check if user already has a pending reservation
            if (reservationRepository.existsByUserEmailAndStartTimeAfter(email, LocalDateTime.now())) {
                logger.warn("Duplicate reservation attempt detected for user: {}", email);
                throw new DuplicateReservationException("User already has an active reservation");
            }

            Reservation reservation = attemptReservation(user);
            logger.info("Successfully created reservation: id={} for user={} at time={}",
                    reservation.getId(), email, reservation.getAvailableSlot().getStartTime());
            meterRegistry.counter("reservation.success").increment();
            return reservation;
        } catch (BusinessException e) {
            logger.error("Failed to create reservation for user: {}. Reason: {}", email, e.getMessage());
            meterRegistry.counter("reservation.failed").increment();
            throw e;
        }
    }

    /**
     * Recovery method for handling OptimisticLockingFailureException when all retry attempts are exhausted.
     * This method must match the signature expected by the @Retryable method but with the exception as the first parameter.
     *
     * @param e The OptimisticLockingFailureException that caused retries to fail
     * @param email The user's email (from the original method parameter)
     * @return Never returns a Reservation, always throws an exception
     * @throws ReservationCapacityExceededException when recovery is needed
     */
    @Recover
    public Reservation recoverFromOptimisticLockingFailure(OptimisticLockingFailureException e, String email) {
        logger.error("Failed to reserve slot after {} attempts due to concurrent modifications", MAX_RETRY_ATTEMPTS);
        meterRegistry.counter("reservation.optimistic_locking_failures").increment();
        throw new ReservationCapacityExceededException("Unable to reserve time slot due to high demand, please try again later");
    }

    /**
     * Helper method to perform a single reservation attempt with optimistic locking.
     *
     * @param user the user making the reservation
     * @return the created reservation
     * @throws ReservationNotAvailableException if no slots are available
     * @throws OptimisticLockingFailureException if concurrent modification is detected
     */
    @Transactional(noRollbackFor = OptimisticLockingFailureException.class)
    protected Reservation attemptReservation(User user) {
        AvailableSlot slot = findNextAvailableSlotCached()
                .orElseThrow(() -> new ReservationNotAvailableException("No available time slots"));

        // Double-check the slot is still available in current database state
        AvailableSlot freshSlot = timeSlotRepository.findById(slot.getId())
                .orElseThrow(() -> new ReservationNotAvailableException("Time slot no longer exists"));

        if (freshSlot.isReserved()) {
            logger.warn("Concurrency issue: Slot {} is already reserved in database.", freshSlot.getId());
            evictNextSlotCache();
            throw new ReservationNotAvailableException("Time slot already reserved");
        }

        freshSlot.setReserved(true);
        AvailableSlot savedSlot = timeSlotRepository.save(freshSlot);
        logger.info("Slot {} reserved for user {}", savedSlot.getId(), user.getEmail());

        evictNextSlotCache();

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setAvailableSlot(savedSlot);
        reservation.setReservedAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        logger.info("Reservation {} created for user {} at slot {}", saved.getId(), user.getEmail(), savedSlot.getId());
        return saved;
    }

    /**
     * Cancels a reservation by its ID and frees the associated time slot. Also evicts the cache.
     *
     * @param id the reservation ID
     * @throws BusinessException if the reservation is not found
     */
    @Transactional
    public void cancelReservation(Long id) {
        logger.info("Attempting to cancel reservation with id: {}", id);
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Reservation not found for id: " + id));

        AvailableSlot slot = reservation.getAvailableSlot();
        slot.setReserved(false);
        timeSlotRepository.save(slot);
        logger.info("Slot {} freed from reservation {}", slot.getId(), id);

        reservationRepository.delete(reservation);
        logger.info("Reservation {} cancelled", id);

        meterRegistry.counter("reservation.cancelled").increment();
        evictNextSlotCache();
    }

    /**
     * Evicts the cache entry for the next available slot.
     */
    private void evictNextSlotCache() {
        cacheableOperations.evictNextSlotCache();
    }
}
