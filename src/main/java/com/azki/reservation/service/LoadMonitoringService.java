package com.azki.reservation.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that monitors system load and determines whether requests should be queued
 * or processed directly based on current traffic levels.
 */
@Service
public class LoadMonitoringService {
    private static final Logger logger = LoggerFactory.getLogger(LoadMonitoringService.class);

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final MeterRegistry meterRegistry;

    @Value("${reservation.request.threshold:5}")
    private int requestThreshold;

    public LoadMonitoringService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.meterRegistry.gauge("reservation.active.requests", activeRequests);
    }

    /**
     * Determines if a request should be queued based on current system load
     *
     * @return true if the request should be queued, false if it can be processed directly
     */
    public boolean shouldQueueRequest() {
        int currentLoad = activeRequests.get();
        boolean shouldQueue = currentLoad >= requestThreshold;

        if (shouldQueue) {
            logger.debug("High load detected ({} active requests). Request will be queued.", currentLoad);
        } else {
            logger.debug("Normal load ({} active requests). Request will be processed directly.", currentLoad);
        }

        return shouldQueue;
    }

    /**
     * Increments the active request counter
     */
    public void incrementActiveRequests() {
        activeRequests.incrementAndGet();
    }

    /**
     * Decrements the active request counter
     */
    public void decrementActiveRequests() {
        activeRequests.decrementAndGet();
    }
}
