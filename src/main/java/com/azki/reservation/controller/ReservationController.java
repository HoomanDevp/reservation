package com.azki.reservation.controller;

import com.azki.reservation.entity.Reservation;
import com.azki.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reservation API", description = "عملیات رزرو زمان")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "رزرو نزدیک‌ترین زمان آزاد با ایمیل کاربر")
    @PostMapping
    public ResponseEntity<Reservation> reserveNearest(@RequestParam String email) {
        try {
            Reservation reservation = reservationService.reserveNearestSlot(email);
            return ResponseEntity.ok(reservation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @Operation(summary = "لغو رزرو با شناسه رزرو")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        try {
            reservationService.cancelReservation(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
