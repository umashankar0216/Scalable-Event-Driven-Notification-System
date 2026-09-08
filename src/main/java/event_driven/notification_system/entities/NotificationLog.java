package event_driven.notification_system.entities;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;


import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notifications_idempotency", columnList = "idempotency_key"),
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_status", columnList = "status")
})
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel; // e.g., "EMAIL", "IN_APP"

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType; // e.g., "USER_LOGIN", "ORDER_PLACED"

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // "PENDING", "DELIVERED", "FAILED"

    @Column(name = "retry_count")
    private Integer retryCount = 0;


    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "error_log", columnDefinition = "text")
    private String errorLog;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Constructors
    public NotificationLog() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getErrorLog() { return errorLog; }
    public void setErrorLog(String errorLog) { this.errorLog = errorLog; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}