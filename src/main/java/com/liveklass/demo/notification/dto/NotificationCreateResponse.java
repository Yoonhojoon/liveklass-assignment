package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.service.NotificationCreateResult;
import com.liveklass.demo.notification.domain.NotificationStatus;

public record NotificationCreateResponse(Long id, NotificationStatus status, boolean duplicated) {

    public static NotificationCreateResponse from(NotificationCreateResult result) {
        return new NotificationCreateResponse(
                result.notificationRequest().getId(),
                result.notificationRequest().getStatus(),
                result.duplicated()
        );
    }
}
