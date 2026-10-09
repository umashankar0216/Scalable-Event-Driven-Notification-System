package event_driven.notification_system.worker;

import com.rabbitmq.client.Channel;
import event_driven.notification_system.dtos.NotificationEventPayload;
import event_driven.notification_system.entities.NotificationLog;
import event_driven.notification_system.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;

@Component
public class EmailConsumerWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumerWorker.class);

    private final TemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;

    @Value("${app.mail.from:notifications@yourtestapp.com}")
    private String fromEmail;

    public EmailConsumerWorker(TemplateEngine templateEngine,
                               JavaMailSender mailSender,
                               NotificationLogRepository logRepository) {
        this.templateEngine = templateEngine;
        this.mailSender = mailSender;
        this.logRepository = logRepository;
    }

    @RabbitListener(queues = "email_queue", ackMode = "MANUAL")
    public void processEmail(NotificationEventPayload payload,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Processing email job for recipient: {}", payload.getRecipient());
        int maxRetries = 3;
        int currentAttempt = 0;
        boolean delivered = false;

        while (currentAttempt < maxRetries && !delivered) {
            try {
                currentAttempt++;

                // 1. Render Thymeleaf HTML body
                Context context = new Context();
                if (payload.getMetadata() != null) {
                    context.setVariables(payload.getMetadata());
                }
                String htmlBody = templateEngine.process("email/welcome", context);

                // 2. Dispatch via Mailtrap SMTP
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setFrom(fromEmail);
                helper.setTo(payload.getRecipient());
                helper.setSubject("Welcome to our Platform!");
                helper.setText(htmlBody, true); // true sets HTML content

                mailSender.send(message);
                log.info("Email delivered successfully to Mailtrap inbox for: {}", payload.getRecipient());
                delivered = true;

                // 3. Mark DELIVERED in Supabase & ACK message
                updateLog(payload.getNotificationLogId(), "DELIVERED", currentAttempt, null);
                channel.basicAck(deliveryTag, false);

            } catch (Exception ex) {
                log.warn("Attempt {} failed for notification {}: {}", currentAttempt, payload.getNotificationLogId(), ex.getMessage());

                if (currentAttempt >= maxRetries) {
                    // Update to FAILED in Supabase and send to Dead-Letter Queue (DLQ)
                    updateLog(payload.getNotificationLogId(), "FAILED", currentAttempt, ex.getMessage());
                    channel.basicNack(deliveryTag, false, false);
                    log.error("Max retries exceeded. Sent to DLQ for payload: {}", payload.getNotificationLogId());
                } else {
                    // Exponential backoff: 2s, 4s, 8s
                    try {
                        Thread.sleep((long) Math.pow(2, currentAttempt) * 1000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void updateLog(java.util.UUID logId, String status, int retries, String error) {
        if (logId == null) return;
        logRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(status);
            logEntry.setRetryCount(retries);
            logEntry.setErrorLog(error);
            logRepository.save(logEntry);
        });
    }
}

