package com.azki.reservation.service;

import com.azki.reservation.entity.Reservation;
import com.azki.reservation.entity.TimeSlot;
import com.azki.reservation.entity.User;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.TimeSlotRepository;
import com.azki.reservation.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReservationService {

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public ReservationService(TimeSlotRepository timeSlotRepository,
                              ReservationRepository reservationRepository,
                              UserRepository userRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Reservation reserveNearestSlot(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        TimeSlot slot = timeSlotRepository.findNextAvailable(LocalDateTime.now())
                .orElseThrow(() -> new IllegalStateException("No available time slots"));

        slot.setReserved(true);
        timeSlotRepository.save(slot);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTimeSlot(slot);
        reservation.setReservedAt(LocalDateTime.now());

        return reservationRepository.save(reservation);
    }

    public void cancelReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        TimeSlot slot = reservation.getTimeSlot();
        slot.setReserved(false);
        timeSlotRepository.save(slot);

        reservationRepository.delete(reservation);
    }
}
