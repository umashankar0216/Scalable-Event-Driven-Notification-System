package event_driven.notification_system.service;

import event_driven.notification_system.dtos.NotificationRequest;

public interface NotificationProducerService {
    boolean processAndPublish(NotificationRequest request);

}
