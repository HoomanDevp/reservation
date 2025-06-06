package com.azki.reservation.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for API rate limiting using the token bucket algorithm.
 * This limits how many requests a client can make in a given time period.
 */
@Configuration
@ConditionalOnProperty(value = "reservation.rate-limiting.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimitConfig {

    // Rate limit: 20 requests per minute with a capacity of 20 tokens
    private static final int CAPACITY = 20;
    private static final int TOKENS_PER_MINUTE = 20;

    @Bean
    public Bucket tokenBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(TOKENS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket4j.builder().addLimit(limit).build();
    }
}
