package com.liveklass.demo.notification.sender;

import com.liveklass.demo.notification.domain.NotificationRequest;

public interface NotificationSender {

    void send(NotificationRequest request);
}
