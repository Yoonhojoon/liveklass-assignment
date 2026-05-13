package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationRequest;

public record NotificationInboxWithJobView(
        NotificationInbox inbox,
        NotificationRequest request,
        NotificationDeliveryJob deliveryJob
) {
}
