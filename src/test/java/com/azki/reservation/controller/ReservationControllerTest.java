package com.azki.reservation.controller;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.azki.reservation.dto.reservation.ReservationResponseDto;
import com.azki.reservation.service.ReservationQueueService;
import com.azki.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationQueueService reservationQueueService;

    @InjectMocks
    private ReservationController reservationController;

    @Test
    void shouldReserveNearest() {
        // Given
        ReservationRequestDto requestDto = new ReservationRequestDto();
        requestDto.setEmail("test@example.com");
        String expectedResponseBody = "request-123";

        when(reservationQueueService.enqueueReservationRequest(any(ReservationRequestDto.class)))
                .thenReturn(expectedResponseBody);

        // When
        ResponseEntity<ReservationResponseDto> response = reservationController.reserveNearest(requestDto);

        // Then
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(expectedResponseBody, response.getBody());
        verify(reservationQueueService).enqueueReservationRequest(requestDto);
    }

    @Test
    void shouldGetReservationStatus() {
        // Given
        String requestId = "request-123";
        String status = "PROCESSING";

        when(reservationQueueService.getRequestStatus(requestId)).thenReturn(status);

        // When
        ResponseEntity<ReservationResponseDto> response = reservationController.getReservationStatus(requestId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(status, response.getBody());
        verify(reservationQueueService).getRequestStatus(requestId);
    }

    @Test
    void shouldReturnNotFoundWhenStatusIsNull() {
        // Given
        String requestId = "request-123";

        when(reservationQueueService.getRequestStatus(requestId)).thenReturn(null);

        // When
        ResponseEntity<ReservationResponseDto> response = reservationController.getReservationStatus(requestId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(reservationQueueService).getRequestStatus(requestId);
    }

    @Test
    void shouldCancelReservation() {
        // Given
        Long reservationId = 1L;
        doNothing().when(reservationService).cancelReservation(reservationId);

        // When
        ResponseEntity<Void> response = reservationController.cancelReservation(reservationId);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(reservationService).cancelReservation(reservationId);
    }
}
