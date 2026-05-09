package com.liveklass.demo.notification.api;

import com.liveklass.demo.notification.application.NotificationCreateResult;
import com.liveklass.demo.notification.domain.NotificationStatus;

public record NotificationCreateResponse(Long id, NotificationStatus status, boolean duplicated) {

    static NotificationCreateResponse from(NotificationCreateResult result) {
        return new NotificationCreateResponse(
                result.notificationRequest().getId(),
                result.notificationRequest().getStatus(),
                result.duplicated()
        );
    }
}
