package com.liveklass.demo.notification.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    @Override
    public void send(NotificationSendCommand command) {
        log.info("[NOTIFICATION][SENDER] notification sent id={} recipientId={} type={} channel={} eventId={}",
                command.id(), command.recipientId(), command.notificationType(),
                command.channel(), command.eventId());
    }
}
