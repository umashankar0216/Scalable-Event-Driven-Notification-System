package event_driven.notification_system.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rabbitmq.client.Channel;
import event_driven.notification_system.dtos.NotificationEventPayload;
import event_driven.notification_system.entities.NotificationLog;
import event_driven.notification_system.repository.NotificationLogRepository;
import event_driven.notification_system.worker.SmsConsumerWorker;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        // Redirects Fast2SMS HTTP calls to WireMock port 8089
        "fast2sms.base-url=http://localhost:8089/dev/bulkV2",
        "fast2sms.api-key=test_wiremock_key"
})
class ExternalSmsClientWireMockTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private SmsConsumerWorker smsConsumerWorker;

    // FIX: Use @MockBean so Spring injects this mock into SmsConsumerWorker
    @MockitoBean
    private NotificationLogRepository logRepository;

    private Channel mockChannel;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8089));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        mockChannel = mock(Channel.class);
    }

    @Test
    @DisplayName("Should successfully dispatch SMS via Fast2SMS when WireMock returns 200 OK")
    void testProcessSms_Fast2Sms_Success() throws IOException {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        NotificationLog mockLog = new NotificationLog();
        mockLog.setId(notificationId);
        mockLog.setStatus("PENDING");

        when(logRepository.findById(notificationId)).thenReturn(Optional.of(mockLog));

        // WireMock intercepts GET /dev/bulkV2 and returns 200 OK
        stubFor(get(urlPathEqualTo("/dev/bulkV2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"return\": true, \"message\": [\"SMS sent successfully\"]}")));

        NotificationEventPayload payload = new NotificationEventPayload();
        payload.setNotificationLogId(notificationId);
        payload.setRecipient("+916302122664");
        payload.setMetadata(Map.of("otp", "778899"));

        // Act
        smsConsumerWorker.processSms(payload, mockChannel, 100L);

        // Assert: Verify WireMock received the actual HTTP request
        verify(1, getRequestedFor(urlPathEqualTo("/dev/bulkV2"))
                .withQueryParam("numbers", equalTo("6302122664")));

        // Verify RabbitMQ acknowledged the message
        verify(mockChannel, times(1)).basicAck(eq(100L), eq(false));
        verify(mockChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());

        // Verify Supabase record updated to DELIVERED
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, atLeastOnce()).save(captor.capture());
        assertEquals("DELIVERED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should retry 3 times on HTTP 500 failure and send message to DLQ via basicNack")
    void testProcessSms_Fast2Sms_ExhaustedRetries_RoutesToDlq() throws IOException {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        NotificationLog mockLog = new NotificationLog();
        mockLog.setId(notificationId);
        mockLog.setStatus("PENDING");

        when(logRepository.findById(notificationId)).thenReturn(Optional.of(mockLog));

        // WireMock simulates persistent 500 errors
        stubFor(get(urlPathEqualTo("/dev/bulkV2"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        NotificationEventPayload payload = new NotificationEventPayload();
        payload.setNotificationLogId(notificationId);
        payload.setRecipient("+916302122664");
        payload.setMetadata(Map.of("otp", "123456"));

        // Act
        smsConsumerWorker.processSms(payload, mockChannel, 200L);

        // Assert: WireMock verifies all 3 retry attempts were made
        verify(3, getRequestedFor(urlPathEqualTo("/dev/bulkV2")));

        // Verify message was rejected to DLQ (basicNack with requeue=false)
        verify(mockChannel, times(1)).basicNack(eq(200L), eq(false), eq(false));
        verify(mockChannel, never()).basicAck(anyLong(), anyBoolean());

        // Verify Supabase record updated to FAILED with 3 retries logged
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, atLeastOnce()).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals(3, captor.getValue().getRetryCount());
    }
}