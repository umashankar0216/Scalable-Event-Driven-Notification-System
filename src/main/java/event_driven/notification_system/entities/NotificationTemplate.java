package event_driven.notification_system.entities;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, unique = true, length = 64)
    private String eventType;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "template_path", nullable = false, length = 255)
    private String templatePath; // e.g., "email/welcome"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    // Constructors
    public NotificationTemplate() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTemplatePath() { return templatePath; }
    public void setTemplatePath(String templatePath) { this.templatePath = templatePath; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}