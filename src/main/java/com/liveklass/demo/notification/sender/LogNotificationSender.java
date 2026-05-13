package com.liveklass.demo.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(NotificationSendCommand command) {
        log.info("[NOTIFICATION][SENDER] notification sent id={} recipientId={} type={} channel={} eventId={}",
                command.id(), command.recipientId(), command.notificationType(),
                command.channel(), command.eventId());
    }
}
