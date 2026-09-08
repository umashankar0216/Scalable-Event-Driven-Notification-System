package event_driven.notification_system.service.IMPL;

import event_driven.notification_system.config.RabbitMQConfig;
import event_driven.notification_system.dtos.NotificationEventPayload;
import event_driven.notification_system.dtos.NotificationRequest;
import event_driven.notification_system.entities.NotificationLog;
import event_driven.notification_system.repository.NotificationLogRepository;
import event_driven.notification_system.service.IdempotencyService;
import event_driven.notification_system.service.NotificationProducerService;
import jakarta.transaction.Transactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationProducerServiceImpl implements NotificationProducerService {
    private final IdempotencyService idempotencyService;
    private final NotificationLogRepository logRepository;
    private final RabbitTemplate rabbitTemplate;

    public NotificationProducerServiceImpl(IdempotencyService idempotencyService,
                                       NotificationLogRepository logRepository,
                                       RabbitTemplate rabbitTemplate) {
        this.idempotencyService = idempotencyService;
        this.logRepository = logRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public boolean processAndPublish(NotificationRequest request) {
        // 1. Check & acquire atomic lock in Upstash Redis or Supabase DB
        boolean isUnique = idempotencyService.lockKey(request.getIdempotencyKey());
        if (!isUnique || logRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            // Duplicate detected; discard immediately
            return false;
        }

        // 2. Persist initial log entry as PENDING in Supabase
        try {
            NotificationLog log = new NotificationLog();
            log.setIdempotencyKey(request.getIdempotencyKey());
            log.setUserId(request.getUserId());
            log.setRecipient(request.getRecipient());
            log.setChannel(request.getChannel());
            log.setEventType(request.getEventType());
            log.setStatus("PENDING");
            log.setMetadata(request.getMetadata());
            NotificationLog savedLog = logRepository.saveAndFlush(log);

            // 3. Prepare payload for the message broker
            NotificationEventPayload payload = new NotificationEventPayload();
            payload.setNotificationLogId(savedLog.getId());
            payload.setUserId(savedLog.getUserId());
            payload.setRecipient(savedLog.getRecipient());
            payload.setEventType(savedLog.getEventType());
            payload.setChannel(savedLog.getChannel());
            payload.setMetadata(savedLog.getMetadata());

            // 4. Publish to CloudAMQP Topic Exchange
            try {
                String routingKey = "notification." + request.getChannel().toLowerCase() + "." + request.getEventType().toLowerCase();
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(NotificationProducerServiceImpl.class)
                        .warn("RabbitMQ connection unavailable ({}); notification saved as PENDING in Supabase.", e.getMessage());
                savedLog.setErrorLog("RabbitMQ connection error: " + e.getMessage());
                logRepository.save(savedLog);
            }

            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            org.slf4j.LoggerFactory.getLogger(NotificationProducerServiceImpl.class)
                    .warn("Duplicate request detected via Supabase database constraint for key: {}", request.getIdempotencyKey());
            return false;
        }
    }
}

