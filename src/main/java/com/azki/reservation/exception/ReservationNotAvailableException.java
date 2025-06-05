package com.azki.reservation.exception;

/**
 * Exception thrown when a reservation is not available for the requested time
 * or when no suitable time slots are available.
 */
public class ReservationNotAvailableException extends BusinessException {
    public ReservationNotAvailableException(String message) {
        super(message);
    }
}
