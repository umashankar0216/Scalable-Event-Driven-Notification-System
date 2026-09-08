package event_driven.notification_system.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class Fast2SmsClient {

    private static final Logger log = LoggerFactory.getLogger(Fast2SmsClient.class);
    private final RestClient restClient;

    @Value("${fast2sms.api-key}")
    private String apiKey;

    public Fast2SmsClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://www.fast2sms.com/dev/bulkV2")
                .build();
    }

    public String sendCustomMessage(String recipientNumber, String messageText) {
        // Strip out country code '+91' or any non-digit characters
        String cleanNumber = recipientNumber.replace("+91", "").replaceAll("[^0-9]", "").trim();

        log.info("Dispatching custom SMS to {}: {}", cleanNumber, messageText);

        // route 'q' is used for custom quick transactional messages
        Map<String, Object> body = Map.of(
                "route", "q",
                "message", messageText,
                "language", "english",
                "flash", 0,
                "numbers", cleanNumber
        );

        return restClient.post()
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}