package com.liveklass.demo.notification.infrastructure.sender;

import com.liveklass.demo.notification.domain.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(NotificationRequest request) {
        log.info("notification sent id={} recipientId={} type={} channel={} eventId={}",
                request.getId(), request.getRecipientId(), request.getNotificationType(),
                request.getChannel(), request.getEventId());
    }
}
