package event_driven.notification_system.service.IMPL;

import event_driven.notification_system.config.RabbitMQConfig;
import event_driven.notification_system.dtos.NotificationEventPayload;
import event_driven.notification_system.dtos.NotificationRequest;
import event_driven.notification_system.entities.NotificationLog;
import event_driven.notification_system.repository.NotificationLogRepository;
import event_driven.notification_system.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProducerServiceImplTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<NotificationLog> logCaptor;

    @Captor
    private ArgumentCaptor<NotificationEventPayload> amqpPayloadCaptor;

    private NotificationProducerServiceImpl producerService;

    @BeforeEach
    void setUp() {
        producerService = new NotificationProducerServiceImpl(
                idempotencyService,
                logRepository,
                rabbitTemplate
        );
    }

    @Test
    @DisplayName("Should successfully process and publish message for unique request")
    void testProcessAndPublish_Success() {
        // 1. Arrange
        NotificationRequest request = new NotificationRequest();
        request.setIdempotencyKey("req_unique_001");
        request.setUserId("user_123");
        request.setRecipient("recipient@example.com");
        request.setChannel("EMAIL");
        request.setEventType("WELCOME_EMAIL");
        request.setMetadata(Map.of("userName", "Alex"));

        when(idempotencyService.lockKey("req_unique_001")).thenReturn(true);
        when(logRepository.existsByIdempotencyKey(any())).thenReturn(false);

        // Stub the database save so it simulates returning a persisted entity with a generated UUID
        when(logRepository.saveAndFlush(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        // 2. Act
        boolean outcome = producerService.processAndPublish(request);

       
        assertTrue(outcome);

        // Verify entity was saved as PENDING
        verify(logRepository, times(1)).saveAndFlush(logCaptor.capture());
        NotificationLog savedEntity = logCaptor.getValue();
        assertEquals("PENDING", savedEntity.getStatus());
        assertEquals("user_123", savedEntity.getUserId());

        // Verify message was published to the correct topic exchange and routing key
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq("notification.email.welcome_email"),
                amqpPayloadCaptor.capture()
        );

        NotificationEventPayload publishedPayload = amqpPayloadCaptor.getValue();
        assertEquals("recipient@example.com", publishedPayload.getRecipient());
        assertNotNull(publishedPayload.getNotificationLogId());
    }

    @Test
    @DisplayName("Should reject processing when idempotency check fails (duplicate request)")
    void testProcessAndPublish_DuplicateRequest_AbortsEarly() {
        // 1. Arrange
        NotificationRequest request = new NotificationRequest();
        request.setIdempotencyKey("req_duplicate_002");

        // Simulate Redis returning false because the key was already locked
        when(idempotencyService.lockKey("req_duplicate_002")).thenReturn(false);

        // 2. Act
        boolean outcome = producerService.processAndPublish(request);

        // 3. Assert
        assertFalse(outcome);

        // Verify downstream systems were NEVER called
        verify(logRepository, never()).saveAndFlush(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}