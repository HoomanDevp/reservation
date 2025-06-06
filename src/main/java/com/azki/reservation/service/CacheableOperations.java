package com.azki.reservation.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.azki.reservation.entity.AvailableSlot;

/**
 * Interface to separate cacheable operations to work around Spring AOP proxy limitations.
 * This solves the @Cacheable self-invocation problem by moving cacheable methods to a separate bean.
 */
public interface CacheableOperations {

    /**
     * Finds and caches the next available time slot.
     *
     * @return Optional containing the next available TimeSlot, or empty if none found.
     */
    Optional<AvailableSlot> findNextAvailableSlotCached(LocalDateTime now);

    /**
     * Evicts the cache entry for the next available slot.
     */
    void evictNextSlotCache();
}
