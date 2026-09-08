package event_driven.notification_system.controller;

import event_driven.notification_system.dtos.NotificationRequest;
import event_driven.notification_system.service.NotificationProducerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationProducerService producerService;

    public NotificationController(NotificationProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping
    public ResponseEntity<String> sendNotification(@RequestBody NotificationRequest request) {
        if (request.getIdempotencyKey() == null || request.getRecipient() == null) {
            return ResponseEntity.badRequest().body("idempotencyKey and recipient are required fields.");
        }

        boolean accepted = producerService.processAndPublish(request);
        if (!accepted) {
            // Rejects duplicate calls gracefully
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Duplicate request ignored.");
        }

        // Returns HTTP 202 Accepted because the job will be processed asynchronously
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Notification queued for delivery.");
    }
}