package com.azki.reservation.controller;

import com.azki.reservation.dto.ApiError;
import com.azki.reservation.exception.BusinessException;
import com.azki.reservation.exception.DuplicateReservationException;
import com.azki.reservation.exception.ReservationCapacityExceededException;
import com.azki.reservation.exception.ReservationNotAvailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that provides consistent error responses across the application.
 * Handles both application-specific and general exceptions with appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateReservationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ApiError> handleDuplicateReservation(DuplicateReservationException ex, HttpServletRequest request) {
        logger.warn("Duplicate reservation attempt: {}", ex.getMessage());
        return buildErrorResponse(ex, "A reservation already exists for this user",
                HttpStatus.CONFLICT, request.getRequestURI());
    }

    @ExceptionHandler(ReservationNotAvailableException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiError> handleReservationNotAvailable(ReservationNotAvailableException ex, HttpServletRequest request) {
        logger.warn("No available slots found: {}", ex.getMessage());
        return buildErrorResponse(ex, "No available time slots found",
                HttpStatus.NOT_FOUND, request.getRequestURI());
    }

    @ExceptionHandler(ReservationCapacityExceededException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ApiError> handleCapacityExceeded(ReservationCapacityExceededException ex, HttpServletRequest request) {
        logger.error("System capacity exceeded: {}", ex.getMessage());
        return buildErrorResponse(ex, "System is currently at full capacity, please try again later",
                HttpStatus.SERVICE_UNAVAILABLE, request.getRequestURI());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        logger.warn("Business rule violation: {}", ex.getMessage());
        return buildErrorResponse(ex, ex.getMessage(),
                HttpStatus.BAD_REQUEST, request.getRequestURI());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ApiError> handleOptimisticLockingFailure(OptimisticLockingFailureException ex, HttpServletRequest request) {
        logger.warn("Concurrent modification detected: {}", ex.getMessage());
        return buildErrorResponse(ex, "The resource was modified by another request, please try again",
                HttpStatus.CONFLICT, request.getRequestURI());
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ApiError> handleRedisConnectionFailure(RedisConnectionFailureException ex, HttpServletRequest request) {
        logger.error("Redis connection failure: {}", ex.getMessage());
        return buildErrorResponse(ex, "Service temporarily unavailable",
                HttpStatus.SERVICE_UNAVAILABLE, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiError> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        logger.warn("Validation failure: {}", ex.getMessage());

        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            validationErrors.put(error.getField(), error.getDefaultMessage())
        );

        ApiError apiError = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "Validation error",
            validationErrors.toString(),
            request.getRequestURI());

        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled exception occurred", ex);
        return buildErrorResponse(ex, "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR, request.getRequestURI());
    }

    private ResponseEntity<ApiError> buildErrorResponse(Exception ex, String message,
                                                       HttpStatus status, String path) {
        ApiError apiError = new ApiError(
            status.value(),
            ex.getMessage(),
            message,
            path);

        return new ResponseEntity<>(apiError, status);
    }
}
