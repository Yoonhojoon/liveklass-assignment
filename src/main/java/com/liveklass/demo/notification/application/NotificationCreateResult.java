package com.liveklass.demo.notification.application;

import com.liveklass.demo.notification.domain.NotificationRequest;

public record NotificationCreateResult(NotificationRequest notificationRequest, boolean duplicated) {
}
