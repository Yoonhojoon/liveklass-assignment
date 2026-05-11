package com.liveklass.demo.notification.controller;

import com.liveklass.demo.common.response.ApiResponse;
import com.liveklass.demo.common.response.CommonSuccessStatus;
import com.liveklass.demo.notification.dto.NotificationCreateRequest;
import com.liveklass.demo.notification.dto.NotificationCreateResponse;
import com.liveklass.demo.notification.dto.NotificationResponse;
import com.liveklass.demo.notification.service.NotificationRequestService;
import com.liveklass.demo.notification.service.dto.NotificationCreateResult;
import jakarta.validation.Valid;
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
    public ResponseEntity<ApiResponse<NotificationCreateResponse>> create(@Valid @RequestBody NotificationCreateRequest request) {
        NotificationCreateResult result = service.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(CommonSuccessStatus.ACCEPTED, NotificationCreateResponse.from(result)));
    }

    @GetMapping("/notifications/{id}")
    public ApiResponse<NotificationResponse> get(@PathVariable Long id) {
        return ApiResponse.success(CommonSuccessStatus.OK, NotificationResponse.from(service.get(id)));
    }

    @GetMapping("/users/{recipientId}/notifications")
    public ApiResponse<List<NotificationResponse>> list(@PathVariable String recipientId, @RequestParam(required = false) Boolean read) {
        List<NotificationResponse> notifications = service.listForRecipient(recipientId, read).stream()
                .map(NotificationResponse::from)
                .toList();
        return ApiResponse.success(CommonSuccessStatus.OK, notifications);
    }

    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id, @RequestHeader("X-User-Id") String recipientId) {
        return ApiResponse.success(CommonSuccessStatus.OK, NotificationResponse.from(service.markRead(id, recipientId)));
    }
}
