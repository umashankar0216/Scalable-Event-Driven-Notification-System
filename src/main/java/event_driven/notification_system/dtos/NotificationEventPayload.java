package event_driven.notification_system.dtos;



import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class NotificationEventPayload implements Serializable {
    private UUID notificationLogId;
    private String userId;
    private String recipient;
    private String eventType;
    private String channel;
    private Map<String, Object> metadata;

    public NotificationEventPayload() {}

    public UUID getNotificationLogId() { return notificationLogId; }
    public void setNotificationLogId(UUID notificationLogId) { this.notificationLogId = notificationLogId; }

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