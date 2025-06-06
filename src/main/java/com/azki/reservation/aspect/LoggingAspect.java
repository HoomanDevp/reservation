package com.azki.reservation.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Arrays;

/**
 * Aspect for logging execution of service and repository Spring components.
 * Logs method calls, parameters, execution time, and exceptions.
 */
@Aspect
@Component
public class LoggingAspect {

    /**
     * Pointcut that matches all repositories, services and controllers.
     */
    @Pointcut("within(@org.springframework.stereotype.Repository *)" +
            " || within(@org.springframework.stereotype.Service *)" +
            " || within(@org.springframework.web.bind.annotation.RestController *)")
    public void springBeanPointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Pointcut that matches all Spring beans in the application's main packages.
     */
    @Pointcut("within(com.azki.reservation..*)" +
            " && !within(com.azki.reservation.aspect..*)")
    public void applicationPackagePointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Retrieves the appropriate logger for the given join point.
     */
    private Logger logger(JoinPoint joinPoint) {
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringTypeName());
    }

    /**
     * Logs before method execution.
     */
    @Before("applicationPackagePointcut() && springBeanPointcut()")
    public void logBefore(JoinPoint joinPoint) {
        Logger log = logger(joinPoint);
        if (log.isDebugEnabled()) {
            log.debug("Enter: {}.{}() with arguments = {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()));
        }
    }

    /**
     * Logs after method execution, including return value.
     */
    @AfterReturning(pointcut = "applicationPackagePointcut() && springBeanPointcut()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        Logger log = logger(joinPoint);
        if (log.isDebugEnabled()) {
            log.debug("Exit: {}.{}() with result = {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                result);
        }
    }

    /**
     * Logs exceptions thrown by methods.
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        Logger log = logger(joinPoint);
        log.error("Exception in {}.{}() with cause = '{}'",
            joinPoint.getSignature().getDeclaringTypeName(),
            joinPoint.getSignature().getName(),
            e.getCause() != null ? e.getCause() : "NULL");

        if (log.isDebugEnabled()) {
            log.debug("Exception details: ", e);
        }
    }

    /**
     * Logs method execution time.
     */
    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger log = logger(joinPoint);
        if (log.isTraceEnabled()) {
            log.trace("Enter: {}.{}() with arguments = {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()));
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = null; // Initialize result to null before try block

        try {
            result = joinPoint.proceed();
            return result;
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument: {} in {}.{}()",
                Arrays.toString(joinPoint.getArgs()),
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName());
            throw e;
        } finally {
            stopWatch.stop();
            if (log.isTraceEnabled()) {
                log.trace("Exit: {}.{}() with result = {} in {}ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    result,
                    stopWatch.getTotalTimeMillis());
            } else if (log.isInfoEnabled() && stopWatch.getTotalTimeMillis() > 500) {
                log.info("Long execution time: {}.{}() took {}ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    stopWatch.getTotalTimeMillis());
            }
        }
    }
}
