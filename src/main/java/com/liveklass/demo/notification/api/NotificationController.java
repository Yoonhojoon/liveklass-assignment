package com.liveklass.demo.notification.api;

import com.liveklass.demo.notification.application.NotificationCreateResult;
import com.liveklass.demo.notification.application.NotificationRequestService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationRequestService service;

    public NotificationController(NotificationRequestService service) {
        this.service = service;
    }

    @PostMapping("/notifications")
    public ResponseEntity<NotificationCreateResponse> create(@RequestBody NotificationCreateRequest request) {
        NotificationCreateResult result = service.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(NotificationCreateResponse.from(result));
    }

    @GetMapping("/notifications/{id}")
    public NotificationResponse get(@PathVariable Long id) {
        return NotificationResponse.from(service.get(id));
    }

    @GetMapping("/users/{recipientId}/notifications")
    public List<NotificationResponse> list(@PathVariable String recipientId, @RequestParam(required = false) Boolean read) {
        return service.listForRecipient(recipientId, read).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PatchMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @RequestHeader("X-User-Id") String recipientId) {
        return NotificationResponse.from(service.markRead(id, recipientId));
    }
}
