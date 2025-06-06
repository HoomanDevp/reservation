package com.azki.reservation.controller;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.azki.reservation.dto.reservation.ReservationResponseDto;
import com.azki.reservation.entity.Reservation;
import com.azki.reservation.service.LoadMonitoringService;
import com.azki.reservation.service.ReservationQueueService;
import com.azki.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reservation API", description = "مدیریت رزرو زمان")
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private static final Logger logger = LoggerFactory.getLogger(ReservationController.class);
    private final ReservationService reservationService;
    private final ReservationQueueService reservationQueueService;
    private final LoadMonitoringService loadMonitoringService;

    @Autowired
    public ReservationController(
            ReservationService reservationService,
            ReservationQueueService reservationQueueService,
            LoadMonitoringService loadMonitoringService) {
        this.reservationService = reservationService;
        this.reservationQueueService = reservationQueueService;
        this.loadMonitoringService = loadMonitoringService;
    }

    @Operation(summary = "رزرو نزدیک‌ترین زمان آزاد")
    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponseDto> reserveNearest(@RequestBody @Valid ReservationRequestDto request) {
        try {
            // Increment active request counter
            loadMonitoringService.incrementActiveRequests();

            // Check if we should queue this request based on current system load
            if (loadMonitoringService.shouldQueueRequest()) {
                // High load - use queue
                logger.info("Processing reservation request for {} through queue due to high load", request.getEmail());
                String requestId = reservationQueueService.enqueueReservationRequest(request);
                String status = reservationQueueService.getRequestStatus(requestId);
                return ResponseEntity.accepted().body(new ReservationResponseDto(requestId, status));
            } else {
                // Normal load - process directly
                logger.info("Processing reservation request for {} directly", request.getEmail());
                Reservation reservation = reservationService.reserveNearestSlot(request.getEmail());
                String requestId = "direct-" + reservation.getId();
                return ResponseEntity.ok().body(new ReservationResponseDto(requestId, "SUCCESS"));
            }
        } finally {
            // Always decrement the counter when processing is complete
            loadMonitoringService.decrementActiveRequests();
        }
    }

    @Operation(summary = "بررسی وضعیت درخواست رزرو با requestId")
    @GetMapping("/status/{requestId}")
    public ResponseEntity<ReservationResponseDto> getReservationStatus(@PathVariable String requestId) {
        String status = reservationQueueService.getRequestStatus(requestId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ReservationResponseDto(requestId, status));
    }

    @Operation(summary = "لغو رزرو با ID")
    @DeleteMapping("/cancel/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }
}

