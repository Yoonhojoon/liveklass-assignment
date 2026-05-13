package com.liveklass.demo.notification.controller;

import com.liveklass.demo.common.response.ApiResponse;
import com.liveklass.demo.common.response.CommonSuccessStatus;
import com.liveklass.demo.notification.dto.NotificationCreateRequest;
import com.liveklass.demo.notification.dto.NotificationCreateResponse;
import com.liveklass.demo.notification.dto.NotificationRetryRequest;
import com.liveklass.demo.notification.dto.NotificationResponse;
import com.liveklass.demo.notification.dto.NotificationTemplateEnabledRequest;
import com.liveklass.demo.notification.dto.NotificationTemplateResponse;
import com.liveklass.demo.notification.dto.NotificationTemplateUpsertRequest;
import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.service.NotificationRetryService;
import com.liveklass.demo.notification.service.NotificationRequestService;
import com.liveklass.demo.notification.service.NotificationTemplateService;
import com.liveklass.demo.notification.service.dto.NotificationCreateResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationRequestService service;
    private final NotificationTemplateService templateService;
    private final NotificationRetryService retryService;

    public NotificationController(NotificationRequestService service, NotificationTemplateService templateService,
            NotificationRetryService retryService) {
        this.service = service;
        this.templateService = templateService;
        this.retryService = retryService;
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

    @PostMapping("/notifications/{id}/retry")
    public ApiResponse<NotificationResponse> retry(
            @PathVariable Long id,
            @RequestHeader("X-Operator-Id") String operatorId,
            @Valid @RequestBody NotificationRetryRequest request
    ) {
        return ApiResponse.success(CommonSuccessStatus.OK, NotificationResponse.from(retryService.retry(id, operatorId, request.reason())));
    }

    @GetMapping("/notification-templates")
    public ApiResponse<List<NotificationTemplateResponse>> listTemplates() {
        return ApiResponse.success(CommonSuccessStatus.OK, templateService.list().stream().map(NotificationTemplateResponse::from).toList());
    }

    @GetMapping("/notification-templates/{notificationType}/{channel}")
    public ApiResponse<NotificationTemplateResponse> getTemplate(
            @PathVariable NotificationType notificationType,
            @PathVariable NotificationChannel channel
    ) {
        return ApiResponse.success(CommonSuccessStatus.OK, NotificationTemplateResponse.from(templateService.get(notificationType, channel)));
    }

    @PostMapping("/notification-templates/{notificationType}/{channel}")
    public ApiResponse<NotificationTemplateResponse> createTemplate(
            @PathVariable NotificationType notificationType,
            @PathVariable NotificationChannel channel,
            @Valid @RequestBody NotificationTemplateUpsertRequest request
    ) {
        return ApiResponse.success(CommonSuccessStatus.OK, NotificationTemplateResponse.from(templateService.create(notificationType, channel, request)));
    }

    @PutMapping("/notification-templates/{notificationType}/{channel}")
    public ApiResponse<NotificationTemplateResponse> updateTemplate(
            @PathVariable NotificationType notificationType,
            @PathVariable NotificationChannel channel,
            @Valid @RequestBody NotificationTemplateUpsertRequest request
    ) {
        return ApiResponse.success(CommonSuccessStatus.OK, NotificationTemplateResponse.from(templateService.update(notificationType, channel, request)));
    }

    @PatchMapping("/notification-templates/{notificationType}/{channel}/enabled")
    public ApiResponse<NotificationTemplateResponse> setTemplateEnabled(
            @PathVariable NotificationType notificationType,
            @PathVariable NotificationChannel channel,
            @Valid @RequestBody NotificationTemplateEnabledRequest request
    ) {
        return ApiResponse.success(CommonSuccessStatus.OK,
                NotificationTemplateResponse.from(templateService.setEnabled(notificationType, channel, request)));
    }

    @DeleteMapping("/notification-templates/{notificationType}/{channel}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable NotificationType notificationType,
            @PathVariable NotificationChannel channel
    ) {
        templateService.delete(notificationType, channel);
        return ResponseEntity.noContent().build();
    }
}
