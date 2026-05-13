package com.liveklass.demo.notification.dto;

import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.service.dto.NotificationCreateResult;
import java.time.Instant;

public record NotificationCreateResponse(Long id, NotificationStatus status, boolean duplicated, Instant scheduledAt) {

    public static NotificationCreateResponse from(NotificationCreateResult result) {
        return new NotificationCreateResponse(
                result.notification().id(),
                result.notification().status(),
                result.duplicated(),
                result.notification().scheduledAt()
        );
    }
}
