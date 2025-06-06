package com.azki.reservation.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * Aspect for database operation logging.
 * Logs database queries, transaction management, and database-related exceptions.
 */
@Aspect
@Component
@Order(2)
public class DatabaseLoggingAspect {

    private static final Logger dbLogger = LoggerFactory.getLogger("DB_OPERATIONS");

    /**
     * Pointcut that matches all repository classes in our application
     */
    @Pointcut("execution(* com.azki.reservation.repository.*.*(..))")
    public void repositoryMethods() {
        // Method is empty as this is just a Pointcut
    }

    /**
     * Pointcut that matches all methods related to database transactions
     */
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalMethods() {
        // Method is empty as this is just a Pointcut
    }

    /**
     * Log the execution time of repository methods and queries
     */
    @Around("repositoryMethods()")
    public Object logQueryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String repoName = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        StopWatch stopWatch = new StopWatch();

        dbLogger.debug("Database operation starting: {}.{}", repoName, methodName);
        stopWatch.start();

        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            stopWatch.stop();
            long executionTime = stopWatch.getTotalTimeMillis();

            // Different logging levels based on query duration
            if (executionTime > 1000) { // Queries taking more than 1 second
                dbLogger.warn("SLOW QUERY: {}.{} - execution time: {}ms",
                    repoName, methodName, executionTime);
            } else if (executionTime > 100) { // Queries taking more than 100ms
                dbLogger.info("Database operation completed: {}.{} - execution time: {}ms",
                    repoName, methodName, executionTime);
            } else {
                dbLogger.debug("Database operation completed: {}.{} - execution time: {}ms",
                    repoName, methodName, executionTime);
            }
            logAffectedRows(methodName, result);
        }
    }

    /**
     * Log database-related exceptions
     */
    @AfterThrowing(pointcut = "repositoryMethods() || transactionalMethods()", throwing = "exception")
    public void logDatabaseException(JoinPoint joinPoint, Exception exception) {
        String methodName = joinPoint.getSignature().getName();
        String typeName = joinPoint.getSignature().getDeclaringTypeName();

        if (exception instanceof DataAccessException) {
            dbLogger.error("Database error in {}.{}: {} - SQL state: {}",
                typeName,
                methodName,
                exception.getMessage(),
                extractSqlState((DataAccessException) exception));
        } else {
            dbLogger.error("Error during database operation {}.{}: {}",
                typeName, methodName, exception.getMessage());
        }
    }

    /**
     * Log transaction boundaries
     */
    @Around("transactionalMethods()")
    public Object logTransactionBoundary(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String typeName = joinPoint.getSignature().getDeclaringTypeName();

        dbLogger.debug("Transaction starting: {}.{}", typeName, methodName);

        try {
            Object result = joinPoint.proceed();
            dbLogger.debug("Transaction committed: {}.{}", typeName, methodName);
            return result;
        } catch (Exception e) {
            dbLogger.warn("Transaction rolled back: {}.{} due to: {}",
                typeName, methodName, e.getMessage());
            throw e;
        }
    }

    /**
     * Helper method to estimate number of affected rows based on repository method result
     */
    private void logAffectedRows(String methodName, Object result) {
        if (methodName.startsWith("save") || methodName.startsWith("update")) {
            dbLogger.debug("Data modified: 1 row affected");
        } else if (methodName.startsWith("delete")) {
            dbLogger.debug("Data deleted");
        } else if (result instanceof Iterable) {
            int count = 0;
            for (Object ignored : (Iterable<?>) result) {
                count++;
            }
            if (count > 0) {
                dbLogger.debug("Data retrieved: {} rows", count);
            }
        }
    }

    /**
     * Helper method to extract SQL state from database exceptions
     */
    private String extractSqlState(DataAccessException ex) {
        try {
            java.sql.SQLException sqlEx = (java.sql.SQLException) ex.getCause();
            return sqlEx != null ? sqlEx.getSQLState() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
