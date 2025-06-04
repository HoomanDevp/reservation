package com.azki.reservation.service;

import com.azki.reservation.entity.Reservation;
import com.azki.reservation.entity.TimeSlot;
import com.azki.reservation.entity.User;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.TimeSlotRepository;
import com.azki.reservation.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for handling reservation operations such as reserving, cancelling, and finding available slots.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    /**
     * Finds and caches the next available time slot.
     *
     * @return Optional containing the next available TimeSlot, or empty if none found.
     */
    @Cacheable(value = "nextSlot", key = "'single'")
    public Optional<TimeSlot> findNextAvailableSlotCached() {
        return timeSlotRepository.findNextAvailable(LocalDateTime.now());
    }

    /**
     * Reserves the nearest available time slot for the given user email with optimistic locking
     * to handle concurrency. Retries up to MAX_RETRY_ATTEMPTS times if reservation fails due to
     * concurrent modification by another transaction.
     *
     * @param email the user's email
     * @return the created Reservation
     * @throws IllegalArgumentException if the user is not found
     * @throws IllegalStateException if no available time slots exist or concurrency issues persist
     */
    @Transactional
    public Reservation reserveNearestSlot(String email) {
        logger.info("Attempting to reserve nearest slot for user: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));

        AtomicInteger attempts = new AtomicInteger(0);
        while (attempts.incrementAndGet() <= MAX_RETRY_ATTEMPTS) {
            try {
                return attemptReservation(user);
            } catch (OptimisticLockingFailureException e) {
                logger.warn("Concurrent modification detected when reserving slot, attempt {}/{}",
                    attempts.get(), MAX_RETRY_ATTEMPTS);

                if (attempts.get() >= MAX_RETRY_ATTEMPTS) {
                    logger.error("Failed to reserve slot after {} attempts due to concurrent modifications", MAX_RETRY_ATTEMPTS);
                    throw new IllegalStateException("Unable to reserve time slot due to high concurrency, please try again later", e);
                }

                // Small delay before retry to reduce contention
                try {
                    Thread.sleep(100L * attempts.get());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Reservation interrupted", ie);
                }
            }
        }

        // This should not be reached due to the exception in the loop
        throw new IllegalStateException("Failed to reserve time slot");
    }

    /**
     * Helper method to perform a single reservation attempt with optimistic locking.
     *
     * @param user the user making the reservation
     * @return the created reservation
     * @throws IllegalStateException if no slots are available
     * @throws OptimisticLockingFailureException if concurrent modification is detected
     */
    @Transactional(noRollbackFor = OptimisticLockingFailureException.class)
    protected Reservation attemptReservation(User user) {
        TimeSlot slot = findNextAvailableSlotCached()
                .orElseThrow(() -> new IllegalStateException("No available time slots"));

        // Double-check the slot is still available in current database state
        TimeSlot freshSlot = timeSlotRepository.findById(slot.getId())
                .orElseThrow(() -> new IllegalStateException("Time slot no longer exists"));

        if (freshSlot.isReserved()) {
            logger.warn("Concurrency issue: Slot {} is already reserved in database.", freshSlot.getId());
            evictNextSlotCache();
            throw new IllegalStateException("Time slot already reserved");
        }

        freshSlot.setReserved(true);
        TimeSlot savedSlot = timeSlotRepository.save(freshSlot);
        logger.info("Slot {} reserved for user {}", savedSlot.getId(), user.getEmail());

        evictNextSlotCache();

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTimeSlot(savedSlot);
        reservation.setReservedAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        logger.info("Reservation {} created for user {} at slot {}", saved.getId(), user.getEmail(), savedSlot.getId());
        return saved;
    }

    /**
     * Cancels a reservation by its ID and frees the associated time slot. Also evicts the cache.
     *
     * @param id the reservation ID
     * @throws IllegalArgumentException if the reservation is not found
     */
    @Transactional
    public void cancelReservation(Long id) {
        logger.info("Attempting to cancel reservation with id: {}", id);
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found for id: " + id));

        TimeSlot slot = reservation.getTimeSlot();
        slot.setReserved(false);
        timeSlotRepository.save(slot);
        logger.info("Slot {} freed from reservation {}", slot.getId(), id);

        reservationRepository.delete(reservation);
        logger.info("Reservation {} cancelled", id);

        evictNextSlotCache();
    }

    /**
     * Evicts the cache entry for the next available slot.
     */
    private void evictNextSlotCache() {
        Optional.ofNullable(cacheManager.getCache("nextSlot"))
                .ifPresent(cache -> cache.evict("single"));
    }
}

