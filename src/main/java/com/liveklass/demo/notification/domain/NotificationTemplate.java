package com.liveklass.demo.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification_template",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_template_type_channel",
                columnNames = {"notification_type", "channel"}
        )
)
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    @Column(name = "message_template", nullable = false, length = 2_000)
    private String messageTemplate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationTemplate(NotificationType notificationType, NotificationChannel channel, String titleTemplate,
            String messageTemplate, boolean enabled) {
        this.notificationType = notificationType;
        this.channel = channel;
        this.titleTemplate = titleTemplate;
        this.messageTemplate = messageTemplate;
        this.enabled = enabled;
    }

    @PrePersist
    void prePersist() {
        Instant now = NotificationTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = NotificationTime.now();
    }

    public void update(String titleTemplate, String messageTemplate, boolean enabled) {
        this.titleTemplate = titleTemplate;
        this.messageTemplate = messageTemplate;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
