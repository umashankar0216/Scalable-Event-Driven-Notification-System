package event_driven.notification_system.dtos;



import java.util.Map;

public class NotificationRequest {
    private String idempotencyKey;
    private String userId;
    private String recipient;
    private String eventType;
    private String channel;
    private Map<String, Object> metadata;

    public NotificationRequest() {}

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
