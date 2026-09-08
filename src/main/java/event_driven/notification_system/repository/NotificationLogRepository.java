package event_driven.notification_system.repository;

import event_driven.notification_system.entities.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    Optional<NotificationLog> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
}