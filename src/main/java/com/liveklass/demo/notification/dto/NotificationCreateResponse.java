package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.service.dto.NotificationCreateResult;

public record NotificationCreateResponse(Long id, NotificationStatus status, boolean duplicated) {

    public static NotificationCreateResponse from(NotificationCreateResult result) {
        return new NotificationCreateResponse(
                result.notification().id(),
                result.notification().status(),
                result.duplicated()
        );
    }
}
