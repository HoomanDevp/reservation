package com.azki.reservation.controller;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.azki.reservation.dto.reservation.ReservationResponseDto;
import com.azki.reservation.entity.Reservation;
import com.azki.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reservation API", description = "مدیریت رزرو زمان")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "رزرو نزدیک‌ترین زمان آزاد")
    @PostMapping
    public ResponseEntity<ReservationResponseDto> reserveNearest(@RequestBody ReservationRequestDto request) {
        try {
            Reservation reservation = reservationService.reserveNearestSlot(request.getEmail());

            return ResponseEntity.ok(new ReservationResponseDto(
                    reservation.getId(),
                    reservation.getTimeSlot().getStartTime().toString(),
                    reservation.getTimeSlot().getEndTime().toString()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "لغو رزرو با ID")
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
