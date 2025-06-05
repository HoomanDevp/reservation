package com.azki.reservation.exception;

/**
 * Exception thrown when a reservation request violates timing constraints
 * (e.g., booking in the past, booking outside business hours, or requesting times that exceed the maximum allowed duration).
 */
public class InvalidReservationTimeException extends BusinessException {
    public InvalidReservationTimeException(String message) {
        super(message);
    }
}
