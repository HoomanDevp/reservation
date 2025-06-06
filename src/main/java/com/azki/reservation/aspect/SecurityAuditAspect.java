package com.azki.reservation.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspect for security audit logging.
 * Logs authentication attempts, successful logins, and access denials.
 */
@Aspect
@Component
@Order(1)
public class SecurityAuditAspect {

    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");

    /**
     * Pointcut for authentication methods in the AuthController
     */
    @Pointcut("execution(* com.azki.reservation.controller.AuthController.*(..))")
    public void authenticationPointcut() {
        // Method is empty as this is just a Pointcut
    }

    /**
     * Pointcut for security filter methods to catch authorization events
     */
    @Pointcut("execution(* com.azki.reservation.security.JwtFilter.doFilter(..))")
    public void authorizationPointcut() {
        // Method is empty as this is just a Pointcut
    }

    /**
     * Log authentication attempts
     */
    @Before("authenticationPointcut() && args(request,..)")
    public void logAuthenticationAttempt(JoinPoint joinPoint, Object request) {
        String methodName = joinPoint.getSignature().getName();

        // Extract username from the request if it's a login attempt
        String username = extractUsername(request);

        if (methodName.toLowerCase().contains("login")) {
            securityLogger.info("Authentication attempt for user: {}", username);
        } else if (methodName.toLowerCase().contains("register")) {
            securityLogger.info("Registration attempt for user: {}", username);
        }
    }

    /**
     * Log successful authentication
     */
    @AfterReturning(pointcut = "authenticationPointcut() && args(request,..)", returning = "result", argNames = "joinPoint,request,result")
    public void logSuccessfulAuthentication(JoinPoint joinPoint, Object request, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String username = extractUsername(request);

        if (methodName.toLowerCase().contains("login") && isSuccessful(result)) {
            securityLogger.info("Successful authentication for user: {}", username);
        } else if (methodName.toLowerCase().contains("register") && isSuccessful(result)) {
            securityLogger.info("Successful registration for user: {}", username);
        }
    }

    /**
     * Log authentication failures
     */
    @AfterThrowing(pointcut = "authenticationPointcut() && args(request,..)", throwing = "exception")
    public void logFailedAuthentication(JoinPoint joinPoint, Object request, Exception exception) {
        String methodName = joinPoint.getSignature().getName();
        String username = extractUsername(request);
        String failureReason = exception.getMessage();

        if (methodName.toLowerCase().contains("login")) {
            securityLogger.warn("Authentication failed for user: {}. Reason: {}", username, failureReason);
        } else if (methodName.toLowerCase().contains("register")) {
            securityLogger.warn("Registration failed for user: {}. Reason: {}", username, failureReason);
        }
    }

    /**
     * Log access denials from security filters
     */
    @AfterThrowing(pointcut = "authorizationPointcut()", throwing = "exception")
    public void logAccessDenial(JoinPoint joinPoint, Exception exception) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";

        securityLogger.warn("Access denied for user: {}. Reason: {}", username, exception.getMessage());
    }

    /**
     * Helper method to extract username from request objects
     */
    private String extractUsername(Object request) {
        try {
            // This will need to be adapted based on your actual DTO structure
            // Using reflection to be flexible with different request types
            return request.getClass().getMethod("getEmail").invoke(request).toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Helper method to determine if the result indicates success
     */
    private boolean isSuccessful(Object result) {
        if (result == null) {
            return false;
        }

        // Adapt this logic based on your API response structure
        try {
            if (result instanceof org.springframework.http.ResponseEntity) {
                org.springframework.http.ResponseEntity<?> response = (org.springframework.http.ResponseEntity<?>) result;
                return response.getStatusCode().is2xxSuccessful();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
