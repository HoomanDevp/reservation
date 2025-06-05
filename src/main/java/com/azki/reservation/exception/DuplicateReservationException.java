package com.azki.reservation.exception;

/**
 * Exception thrown when a user attempts to make a duplicate reservation
 * (e.g., booking the same slot twice).
 */
public class DuplicateReservationException extends BusinessException {
    public DuplicateReservationException(String message) {
        super(message);
    }
}
