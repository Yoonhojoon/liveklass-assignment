package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationTemplate;
import com.liveklass.demo.notification.domain.NotificationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByNotificationTypeAndChannel(NotificationType notificationType, NotificationChannel channel);
}
