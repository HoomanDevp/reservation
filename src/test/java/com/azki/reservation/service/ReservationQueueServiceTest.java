package com.azki.reservation.service;

import com.azki.reservation.dto.reservation.ReservationRequestDto;
import com.azki.reservation.exception.DuplicateReservationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationQueueServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private ReservationQueueService queueService;
    private final RedisCleanupService redisCleanupService;

    ReservationQueueServiceTest(ObjectMapper objectMapper, MeterRegistry meterRegistry, ReservationQueueService queueService, RedisCleanupService redisCleanupService) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.queueService = queueService;
        this.redisCleanupService = redisCleanupService;
    }


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        queueService = new ReservationQueueService(redisTemplate, reservationService, objectMapper, meterRegistry,redisCleanupService);
    }

    @Test
    void shouldEnqueueReservationRequest() {
        // Given
        ReservationRequestDto request = new ReservationRequestDto();
        request.setEmail("test@example.com");

        // When
        String requestId = queueService.enqueueReservationRequest(request);

        // Then
        assertNotNull(requestId);
        verify(listOperations).rightPush(anyString(), anyString());
        verify(valueOperations).set(contains("reservation:status:"), eq(ReservationQueueService.RequestStatus.QUEUED.name()));
    }

    @Test
    void shouldGetRequestStatus() {
        // Given
        String requestId = "test-request-id";
        String status = ReservationQueueService.RequestStatus.PROCESSING.name();
        when(valueOperations.get(anyString())).thenReturn(status);

        // When
        String result = queueService.getRequestStatus(requestId);

        // Then
        assertEquals(status, result);
        verify(valueOperations).get("reservation:status:" + requestId);
    }

    @Test
    void shouldReturnNullWhenRequestStatusNotFound() {
        // Given
        String requestId = "test-request-id";
        when(valueOperations.get(anyString())).thenReturn(null);

        // When
        String result = queueService.getRequestStatus(requestId);

        // Then
        assertNull(result);
        verify(valueOperations).get("reservation:status:" + requestId);
    }

    @Test
    void shouldReportQueueLength() {
        // Given
        Long expectedLength = 5L;
        when(listOperations.size(anyString())).thenReturn(expectedLength);

        // When
        long length = queueService.getQueueLength();

        // Then
        assertEquals(expectedLength, length);
        verify(listOperations).size("reservation:queue");
    }

    @Test
    void shouldHandleDuplicateReservationException() {
        // Given
        ReservationRequestDto request = new ReservationRequestDto();
        request.setEmail("test@example.com");

        when(queueService.isUserAlreadyInQueue(anyString())).thenReturn(true);

        // When/Then
        assertThrows(DuplicateReservationException.class, () -> queueService.enqueueReservationRequest(request));

        verify(redisTemplate, never()).opsForList();
    }
}
