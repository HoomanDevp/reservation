package com.azki.reservation.service;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing reservation requests asynchronously using a Redis-backed queue.
 * <p>
 * This service provides methods to enqueue reservation requests as JSON strings into a Redis list,
 * and a scheduled background worker to dequeue and process these requests by delegating to
 * {@link ReservationService}. Serialization and deserialization are handled using Jackson's ObjectMapper.
 * <p>
 * This design allows the system to handle high concurrency by decoupling incoming API requests from
 * direct database writes, improving scalability and reliability.
 */
@Service
public class ReservationQueueService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private static final Logger logger = LoggerFactory.getLogger(ReservationQueueService.class);
    private static final String QUEUE_KEY = "reservation:queue";
    private static final String DLQ_KEY = "reservation:dlq";
    private static final int MAX_ATTEMPTS = 3;
    private static final String STATUS_KEY_PREFIX = "reservation:status:";
    @Value("${reservation.queue.batch-size:10}")
    private int batchSize;

    private volatile boolean running = true;

    public enum RequestStatus {
        QUEUED, PROCESSING, SUCCESS, FAILED
    }

    /**
     * Helper class to wrap reservation request and attempt count for DLQ support.
     */
    private static class QueueItem {
        public ReservationRequestDto request;
        public int attempts;
        public QueueItem(ReservationRequestDto request, int attempts) {
            this.request = request;
            this.attempts = attempts;
        }
    }

    public String enqueueReservationRequest(Object reservationRequest) {
        String requestId = UUID.randomUUID().toString();
        try {
            String json = objectMapper.writeValueAsString(new QueueItem((ReservationRequestDto) reservationRequest, 0));
            redisTemplate.opsForList().rightPush(QUEUE_KEY, json);
            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.QUEUED.name());
        } catch (Exception e) {
            logger.error("Failed to serialize reservation request: {}", reservationRequest, e);
        }
        return requestId;
    }

    public String getRequestStatus(String requestId) {
        Object status = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + requestId);
        return status != null ? status.toString() : null;
    }

    private QueueItem dequeueQueueItem() {
        Object jsonObj = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (jsonObj instanceof String json) {
            try {
                return objectMapper.readValue(json, QueueItem.class);
            } catch (Exception e) {
                logger.error("Failed to deserialize queue item: {}", json, e);
            }
        }
        return null;
    }

    private void moveToDLQ(QueueItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForList().rightPush(DLQ_KEY, json);
            meterRegistry.counter("reservation.dlq.moved").increment();
            logger.warn("Moved reservation request to DLQ: {}", item.request);
        } catch (Exception e) {
            logger.error("Failed to move reservation request to DLQ: {}", item, e);
        }
    }

    /**
     * Graceful shutdown: stop processing new batches when the application is shutting down.
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        logger.info("ReservationQueueService is shutting down. No new batches will be processed.");
    }

    /**
     * Returns the current queue length for monitoring.
     */
    public long getQueueLength() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * Returns the current DLQ length for monitoring.
     */
    public long getDLQLength() {
        Long size = redisTemplate.opsForList().size(DLQ_KEY);
        return size != null ? size : 0;
    }

    /**
     * Returns the number of failed attempts for a given requestId (if available).
     */
    public Integer getFailedAttempts(String requestId) {
        // This is a simple implementation; for more advanced tracking, store attempts in Redis per requestId.
        // Not implemented in this version for brevity.
        return null;
    }

    /**
     * Ensures idempotency by checking if a request with the same requestId has already succeeded.
     */
    private boolean isAlreadyProcessed(String requestId) {
        String status = getRequestStatus(requestId);
        return RequestStatus.SUCCESS.name().equals(status);
    }

    // Metrics registration in constructor
    public ReservationQueueService(
        RedisTemplate<String, Object> redisTemplate,
        ReservationService reservationService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.reservationService = reservationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("reservation.queue.length", this, ReservationQueueService::getQueueLength);
        meterRegistry.gauge("reservation.dlq.length", this, ReservationQueueService::getDLQLength);
    }

    @Scheduled(fixedDelayString = "${reservation.queue.poll-interval-ms:100}")
    public void processReservationQueue() {
        if (!running) return;
        for (;;) {
            List<Object> batch = redisTemplate.opsForList().range(QUEUE_KEY, 0, batchSize - 1);
            if (batch == null || batch.isEmpty()) break;
            for (Object jsonObj : batch) {
                QueueItem item = null;
                if (jsonObj instanceof String json) {
                    try {
                        item = objectMapper.readValue(json, QueueItem.class);
                    } catch (Exception e) {
                        logger.error("Failed to deserialize queue item: {}", json, e);
                        meterRegistry.counter("reservation.queue.deserialize.errors").increment();
                        continue;
                    }
                }
                if (item == null) continue;
                String requestId = item.request.getRequestId();
                if (requestId != null) {
                    if (isAlreadyProcessed(requestId)) {
                        redisTemplate.opsForList().leftPop(QUEUE_KEY);
                        continue;
                    }
                    redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.PROCESSING.name());
                }
                try {
                    reservationService.reserveNearestSlot(item.request.getEmail());
                    meterRegistry.counter("reservation.queue.processed").increment();
                    if (requestId != null) {
                        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.SUCCESS.name());
                    }
                    redisTemplate.opsForList().leftPop(QUEUE_KEY);
                } catch (Exception e) {
                    item.attempts++;
                    logger.error("Failed to process reservation request (attempt {}): {}", item.attempts, item.request, e);
                    meterRegistry.counter("reservation.queue.process.errors").increment();
                    if (item.attempts >= MAX_ATTEMPTS) {
                        moveToDLQ(item);
                        if (requestId != null) {
                            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.FAILED.name());
                        }
                        redisTemplate.opsForList().leftPop(QUEUE_KEY);
                    } else {
                        try {
                            String updatedJson = objectMapper.writeValueAsString(item);
                            redisTemplate.opsForList().set(QUEUE_KEY, 0, updatedJson);
                        } catch (Exception ex) {
                            logger.error("Failed to re-enqueue reservation request: {}", item, ex);
                            moveToDLQ(item);
                            if (requestId != null) {
                                redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.FAILED.name());
                            }
                            redisTemplate.opsForList().leftPop(QUEUE_KEY);
                        }
                    }
                }
            }
        }
    }
}
