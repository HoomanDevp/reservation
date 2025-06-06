package com.azki.reservation.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics to enhance monitoring and observability.
 * Defines metrics that help track system performance and health.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Timer reservationProcessingTimer(MeterRegistry registry) {
        return Timer.builder("reservation.processing.time")
                .description("Time taken to process a reservation")
                .register(registry);
    }

    @Bean
    public Timer slotSelectionTimer(MeterRegistry registry) {
        return Timer.builder("reservation.slot.selection.time")
                .description("Time taken to select an available slot")
                .register(registry);
    }
}
