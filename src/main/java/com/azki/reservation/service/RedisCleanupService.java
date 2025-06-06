package com.azki.reservation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for cleaning up Redis resources to prevent memory growth.
 * Periodically scans and removes old keys related to reservation status tracking.
 */
@Service
public class RedisCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCleanupService.class);
    private static final String STATUS_KEY_PREFIX = "reservation:status:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${reservation.status.expiry-hours:24}")
    private int statusExpiryHours;

    public RedisCleanupService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Apply TTL to new status keys when they're created
     * @param key The status key
     */
    public void setExpiryOnStatusKey(String key) {
        redisTemplate.expire(key, statusExpiryHours, TimeUnit.HOURS);
    }

    /**
     * Runs every day at 2 AM to clean up old status keys
     * that might have been created before TTL was implemented
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldStatusKeys() {
        logger.info("Starting scheduled cleanup of old reservation status keys");
        try {
            // Use scan command with count option to avoid blocking Redis
            Set<String> keys = redisTemplate.keys(STATUS_KEY_PREFIX + "*");

            if (!keys.isEmpty()) {
                int count = 0;
                for (String key : keys) {
                    // Check if key already has TTL, if not set it
                    Duration ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS) > 0
                        ? null : Duration.ofHours(statusExpiryHours);

                    if (ttl != null) {
                        redisTemplate.expire(key, statusExpiryHours, TimeUnit.HOURS);
                        count++;
                    }
                }
                logger.info("Applied TTL to {} status keys", count);
            } else {
                logger.info("No status keys found to clean up");
            }
        } catch (Exception e) {
            logger.error("Error during status keys cleanup", e);
        }
    }
}
