package com.liveklass.demo.notification.infrastructure.sender;

import com.liveklass.demo.notification.domain.NotificationRequest;

public interface NotificationSender {

    void send(NotificationRequest request);
}
