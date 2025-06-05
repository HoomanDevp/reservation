package com.azki.reservation.controller;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.azki.reservation.service.ReservationQueueService;
import com.azki.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Reservation API", description = "مدیریت رزرو زمان")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationQueueService reservationQueueService;

    @Autowired
    public ReservationController(ReservationService reservationService, ReservationQueueService reservationQueueService) {
        this.reservationService = reservationService;
        this.reservationQueueService = reservationQueueService;
    }

    @Operation(summary = "رزرو نزدیک‌ترین زمان آزاد")
    @PostMapping
    public ResponseEntity<String> reserveNearest(@RequestBody @Valid ReservationRequestDto request) {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);
        return ResponseEntity.accepted().body(reservationQueueService.enqueueReservationRequest(request));
    }

    @Operation(summary = "بررسی وضعیت درخواست رزرو با requestId")
    @GetMapping("/status/{requestId}")
    public ResponseEntity<String> getReservationStatus(@PathVariable String requestId) {
        String status = reservationQueueService.getRequestStatus(requestId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "لغو رزرو با ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }
}
