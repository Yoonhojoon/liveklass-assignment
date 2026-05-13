package com.liveklass.demo.notification.sender;

public interface NotificationSender {

    void send(NotificationSendCommand command);
}
