package com.azki.reservation.service;

import com.azki.reservation.entity.AvailableSlot;
import com.azki.reservation.entity.Reservation;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.TimeSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for managing reservation expirations and
 * automatically freeing up unclaimed slots.
 */
@Service
public class ReservationExpiryService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationExpiryService.class);

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final CacheableOperations cacheableOperations;

    @Value("${reservation.expiry.hours:24}")
    private int expiryHours;

    public ReservationExpiryService(
            ReservationRepository reservationRepository,
            TimeSlotRepository timeSlotRepository,
            CacheableOperations cacheableOperations) {
        this.reservationRepository = reservationRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.cacheableOperations = cacheableOperations;
    }

    /**
     * Scheduled task that runs at a configured interval to detect and handle expired reservations.
     * Default is to run every 15 minutes.
     */
    @Scheduled(fixedDelayString = "${reservation.expiry.check-minutes:15}000")
    @Transactional
    public void processExpiredReservations() {
        logger.info("Starting expired reservations check");

        LocalDateTime expirationThreshold = LocalDateTime.now().minusHours(expiryHours);
        List<Reservation> expiredReservations =
            reservationRepository.findExpiredReservations(expirationThreshold);

        if (expiredReservations.isEmpty()) {
            logger.info("No expired reservations found");
            return;
        }

        logger.info("Found {} expired reservations to process", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                // Free up the time slot
                AvailableSlot slot = reservation.getAvailableSlot();
                slot.setReserved(false);
                timeSlotRepository.save(slot);

                // Delete the reservation
                reservationRepository.delete(reservation);

                logger.info("Expired reservation deleted: id={}, user={}, slot={}",
                    reservation.getId(), reservation.getUser().getEmail(),
                    reservation.getAvailableSlot().getId());

            } catch (Exception e) {
                logger.error("Error processing expired reservation {}", reservation.getId(), e);
            }
        }

        // Clear cache to reflect the newly available slots
        cacheableOperations.evictNextSlotCache();

        logger.info("Completed expired reservations cleanup, processed {} reservations",
            expiredReservations.size());
    }
}
