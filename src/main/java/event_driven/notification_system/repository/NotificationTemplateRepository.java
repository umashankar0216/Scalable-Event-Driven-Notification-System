package event_driven.notification_system.repository;


import event_driven.notification_system.entities.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByEventTypeAndChannel(String eventType, String channel);
}