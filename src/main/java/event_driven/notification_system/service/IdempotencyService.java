package event_driven.notification_system.service;

public interface IdempotencyService {
    boolean lockKey(String idempotencyKey);
}
