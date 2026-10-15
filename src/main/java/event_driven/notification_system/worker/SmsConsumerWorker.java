package event_driven.notification_system.worker;

import com.rabbitmq.client.Channel;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import event_driven.notification_system.dtos.NotificationEventPayload;
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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class SmsConsumerWorker {

    private static final Logger log = LoggerFactory.getLogger(SmsConsumerWorker.class);

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    @Value("${fast2sms.api-key}")
    private String fast2SmsApiKey;

    private final NotificationLogRepository logRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

                // Extract OTP from metadata
                String otp = (payload.getMetadata() != null && payload.getMetadata().containsKey("otp"))
                        ? String.valueOf(payload.getMetadata().get("otp"))
                        : "000000";

                String recipient = payload.getRecipient();

                // Route: Indian numbers (+91) → Fast2SMS, others → Twilio
                if (isIndianNumber(recipient)) {
                    sendViaFast2Sms(recipient, otp);
                    log.info("SMS delivered successfully via Fast2SMS to: {}", recipient);
                } else {
                    sendViaTwilio(recipient, otp);
                    log.info("SMS delivered successfully via Twilio to: {}", recipient);
                }

                delivered = true;
                updateNotificationLog(payload.getNotificationLogId(), "DELIVERED", currentAttempt, null);
                channel.basicAck(deliveryTag, false);

            } catch (Exception ex) {
                log.warn("Attempt {} failed for SMS notification {}: {}",
                        currentAttempt, payload.getNotificationLogId(), ex.getMessage());

                if (currentAttempt >= maxRetries) {
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

    /**
     * Sends SMS via Fast2SMS Quick Route — no DLT registration required.
     * Works for all Indian (+91) numbers.
     */
    private void sendViaFast2Sms(String recipient, String otp) throws Exception {
        // Strip country code — Fast2SMS expects 10-digit mobile number
        String mobile = recipient.startsWith("+91") ? recipient.substring(3)
                      : recipient.startsWith("91") && recipient.length() == 12 ? recipient.substring(2)
                      : recipient;

        String message = URLEncoder.encode("Your OTP is: " + otp + ". Valid for 10 minutes.", StandardCharsets.UTF_8);

        String url = "https://www.fast2sms.com/dev/bulkV2"
                + "?authorization=" + fast2SmsApiKey
                + "&route=q"
                + "&message=" + message
                + "&numbers=" + mobile;

        log.info("Sending SMS via Fast2SMS to mobile: {}", mobile);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Fast2SMS response [{}]: {}", response.statusCode(), response.body());

        if (response.statusCode() != 200 || response.body().contains("\"return\":false")) {
            throw new RuntimeException("Fast2SMS delivery failed: " + response.body());
        }
    }

    /**
     * Sends SMS via Twilio — used for international (non-Indian) numbers.
     */
    private void sendViaTwilio(String recipient, String otp) {
        String messageBody = "Your OTP is: " + otp + ". Valid for 10 minutes.";
        Message message = Message.creator(
                new PhoneNumber(recipient),
                new PhoneNumber(twilioPhoneNumber),
                messageBody
        ).create();
        log.info("Twilio SMS SID: {}", message.getSid());
    }

    /**
     * Returns true if the phone number is an Indian number (+91 or 91 prefix with 12 digits).
     */
    private boolean isIndianNumber(String phoneNumber) {
        return phoneNumber != null &&
               (phoneNumber.startsWith("+91") || (phoneNumber.startsWith("91") && phoneNumber.length() == 12));
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
