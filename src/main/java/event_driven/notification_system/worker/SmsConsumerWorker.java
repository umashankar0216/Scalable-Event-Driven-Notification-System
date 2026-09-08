package event_driven.notification_system.worker;

import com.rabbitmq.client.Channel;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import event_driven.notification_system.dtos.NotificationEventPayload;
import event_driven.notification_system.entities.NotificationLog;
import event_driven.notification_system.repository.NotificationLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SmsConsumerWorker {

    private static final Logger log = LoggerFactory.getLogger(SmsConsumerWorker.class);

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    private final NotificationLogRepository logRepository;

    public SmsConsumerWorker(NotificationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @PostConstruct
    public void initTwilio() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio client initialized successfully.");
    }

    @RabbitListener(queues = "sms_queue", ackMode = "MANUAL")
    public void processSms(NotificationEventPayload payload,
                           Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Processing SMS job for recipient: {}", payload.getRecipient());
        int maxRetries = 3;
        int currentAttempt = 0;
        boolean delivered = false;

        while (currentAttempt < maxRetries && !delivered) {
            try {
                currentAttempt++;

                // 1. Build SMS body from metadata
                String otp = (payload.getMetadata() != null && payload.getMetadata().containsKey("otp"))
                        ? String.valueOf(payload.getMetadata().get("otp"))
                        : "";

                String messageBody = "Your verification code is: " + otp;

                // 2. Dispatch SMS via Twilio SDK
                Message message = Message.creator(
                        new PhoneNumber(payload.getRecipient()),
                        new PhoneNumber(twilioPhoneNumber),
                        messageBody
                ).create();

                log.info("SMS delivered successfully! Twilio SID: {}", message.getSid());
                delivered = true;

                // 3. Mark DELIVERED in Supabase & ACK RabbitMQ message
                updateNotificationLog(payload.getNotificationLogId(), "DELIVERED", currentAttempt, null);
                channel.basicAck(deliveryTag, false);

            } catch (Exception ex) {
                log.warn("Attempt {} failed for SMS notification {}: {}",
                        currentAttempt, payload.getNotificationLogId(), ex.getMessage());

                if (currentAttempt >= maxRetries) {
                    // Update log to FAILED and route to Dead-Letter Queue (DLQ)
                    updateNotificationLog(payload.getNotificationLogId(), "FAILED", currentAttempt, ex.getMessage());
                    channel.basicNack(deliveryTag, false, false);
                    log.error("Max retries exceeded for SMS. Message routed to DLQ.");
                } else {
                    // Exponential backoff: 2s, 4s, 8s
                    try {
                        Thread.sleep((long) Math.pow(2, currentAttempt) * 1000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void updateNotificationLog(java.util.UUID logId, String status, int retries, String errorLog) {
        if (logId == null) return;
        logRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(status);
            logEntry.setRetryCount(retries);
            logEntry.setErrorLog(errorLog);
            logRepository.save(logEntry);
        });
    }

}
//package event_driven.notification_system.worker;
//
//import com.rabbitmq.client.Channel;
//import event_driven.notification_system.client.Fast2SmsClient;
//import event_driven.notification_system.dtos.NotificationEventPayload;
//import event_driven.notification_system.repository.NotificationLogRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.amqp.support.AmqpHeaders;
//import org.springframework.messaging.handler.annotation.Header;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//
//@Component
//public class SmsConsumerWorker {
//
//    private static final Logger log = LoggerFactory.getLogger(SmsConsumerWorker.class);
//
//    private final Fast2SmsClient fast2SmsClient;
//    private final NotificationLogRepository logRepository;
//
//    public SmsConsumerWorker(Fast2SmsClient fast2SmsClient, NotificationLogRepository logRepository) {
//        this.fast2SmsClient = fast2SmsClient;
//        this.logRepository = logRepository;
//    }
//
//    @RabbitListener(queues = "sms_queue", ackMode = "MANUAL")
//    public void processSms(NotificationEventPayload payload,
//                           Channel channel,
//                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
//
//        log.info("Processing SMS job for recipient: {}", payload.getRecipient());
//        int maxRetries = 3;
//        int currentAttempt = 0;
//        boolean delivered = false;
//
//        while (currentAttempt < maxRetries && !delivered) {
//            try {
//                currentAttempt++;
//
//                // Build custom text message from payload
//                String messageBody;
//                if (payload.getMetadata() != null && payload.getMetadata().containsKey("message")) {
//                    messageBody = String.valueOf(payload.getMetadata().get("message"));
//                } else if (payload.getMetadata() != null && payload.getMetadata().containsKey("otp")) {
//                    messageBody = "Your verification code is: " + payload.getMetadata().get("otp");
//                } else {
//                    messageBody = "Your order update for event: " + payload.getEventType();
//                }
//
//                // Call Fast2SMS API with custom text
//                String response = fast2SmsClient.sendCustomMessage(payload.getRecipient(), messageBody);
//                log.info("Fast2SMS Response: {}", response);
//
//                delivered = true;
//                updateNotificationLog(payload.getNotificationLogId(), "DELIVERED", currentAttempt, null);
//                channel.basicAck(deliveryTag, false);
//
//            } catch (Exception ex) {
//                log.warn("Attempt {} failed for SMS notification {}: {}",
//                        currentAttempt, payload.getNotificationLogId(), ex.getMessage());
//
//                if (currentAttempt >= maxRetries) {
//                    updateNotificationLog(payload.getNotificationLogId(), "FAILED", currentAttempt, ex.getMessage());
//                    channel.basicNack(deliveryTag, false, false);
//                    log.error("Max retries exceeded for SMS. Message routed to DLQ.");
//                } else {
//                    try {
//                        Thread.sleep((long) Math.pow(2, currentAttempt) * 1000);
//                    } catch (InterruptedException ignored) {}
//                }
//            }
//        }
//    }
//
//    private void updateNotificationLog(java.util.UUID logId, String status, int retries, String errorLog) {
//        if (logId == null) return;
//        logRepository.findById(logId).ifPresent(logEntry -> {
//            logEntry.setStatus(status);
//            logEntry.setRetryCount(retries);
//            logEntry.setErrorLog(errorLog);
//            logRepository.save(logEntry);
//        });
//    }
//}