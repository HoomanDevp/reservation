package com.azki.reservation.exception;

/**
 * Exception thrown when the system has reached capacity limits
 * (e.g., no more slots available for a given day or resource).
 */
public class ReservationCapacityExceededException extends BusinessException {
    public ReservationCapacityExceededException(String message) {
        super(message);
    }
}
