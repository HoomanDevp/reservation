package com.azki.reservation.service;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.azki.reservation.exception.BusinessException;
import com.azki.reservation.exception.DuplicateReservationException;
import com.azki.reservation.exception.ReservationCapacityExceededException;
import com.azki.reservation.exception.ReservationNotAvailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;

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
    private static final String EMAIL_SET_KEY = "reservation:emails:queued"; // Key for tracking emails in queue
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
        public String requestId; // Added requestId field


        public QueueItem(ReservationRequestDto request, int attempts, String requestId) {
            this.request = request;
            this.attempts = attempts;
            this.requestId = requestId;
        }
    }

    public String enqueueReservationRequest(Object reservationRequest) {
        String requestId = UUID.randomUUID().toString();
        try {
            ReservationRequestDto req = (ReservationRequestDto) reservationRequest;
            if (isUserAlreadyInQueue(req.getEmail())) {
                throw new DuplicateReservationException("A reservation request for this email is already in queue");
            }

            String json = objectMapper.writeValueAsString(new QueueItem((ReservationRequestDto) reservationRequest, 0, requestId));
            redisTemplate.opsForList().rightPush(QUEUE_KEY, json);
            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.QUEUED.name());
            redisTemplate.opsForSet().add(EMAIL_SET_KEY, req.getEmail()); // Add email to set
        } catch (DuplicateReservationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to serialize reservation request: {}", reservationRequest, e);
            throw new BusinessException("Failed to process reservation request: " + e.getMessage());
        }
        return requestId;
    }

    boolean isUserAlreadyInQueue(String email) {
        // Check if the email is in the Redis set for O(1) lookup
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(EMAIL_SET_KEY, email));
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
     * Ensures idempotency by checking if a request with the same requestId has already succeeded.
     */
    private boolean isAlreadyProcessed(String requestId) {
        String status = getRequestStatus(requestId);
        return RequestStatus.SUCCESS.name().equals(status);
    }

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
        for (int i = 0; i < batchSize; i++) {
            QueueItem item = dequeueQueueItem();
            if (item == null) break;

            String requestId = item.requestId; // Use requestId directly from QueueItem
            if (requestId != null) {
                if (isAlreadyProcessed(requestId)) {
                    continue; // Item already popped by dequeueQueueItem
                }
                redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.PROCESSING.name());
            }

            try {
                reservationService.reserveNearestSlot(item.request.getEmail());
                meterRegistry.counter("reservation.queue.processed").increment();
                if (requestId != null) {
                    redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.SUCCESS.name());
                }
                // Remove email from tracking set after successful processing
                redisTemplate.opsForSet().remove(EMAIL_SET_KEY, item.request.getEmail());
            } catch (DuplicateReservationException e) {
                logger.info("Skipping duplicate reservation: {}", item.request.getEmail());
                meterRegistry.counter("reservation.queue.duplicate").increment();
                if (requestId != null) {
                    redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.FAILED.name() + ": " + e.getMessage());
                }
                // Remove email from tracking set as this request is now completed (failed)
                redisTemplate.opsForSet().remove(EMAIL_SET_KEY, item.request.getEmail());
            } catch (ReservationNotAvailableException e) {
                logger.info("No slots available for reservation: {}", item.request.getEmail());
                meterRegistry.counter("reservation.queue.no_slots").increment();
                if (requestId != null) {
                    redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, RequestStatus.FAILED.name() + ": " + e.getMessage());
                }
                // Remove email from tracking set as this request is now completed (failed)
                redisTemplate.opsForSet().remove(EMAIL_SET_KEY, item.request.getEmail());
            } catch (ReservationCapacityExceededException e) {
                handleRetryableError(item, requestId, e, "capacity_exceeded");
            } catch (BusinessException e) {
                handleRetryableError(item, requestId, e, "business_rule");
            } catch (Exception e) {
                handleRetryableError(item, requestId, e, "technical");
            }
        }
    }

    private void handleRetryableError(QueueItem item, String requestId, Exception e, String errorType) {
        item.attempts++;
        logger.error("Failed to process reservation request (attempt {}, type: {}): {}", item.attempts, errorType, item.request, e);
        meterRegistry.counter("reservation.queue.process.errors." + errorType).increment();
        if (item.attempts >= MAX_ATTEMPTS) {
            moveToDLQ(item);
            if (requestId != null) {
                redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId,
                    RequestStatus.FAILED.name() + ": " + e.getMessage());
            }
            // Remove email from tracking set when max retries are exhausted
            redisTemplate.opsForSet().remove(EMAIL_SET_KEY, item.request.getEmail());
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
                // Remove email from tracking set when request can't be re-enqueued
                redisTemplate.opsForSet().remove(EMAIL_SET_KEY, item.request.getEmail());
                redisTemplate.opsForList().leftPop(QUEUE_KEY);
            }
        }
    }
}
