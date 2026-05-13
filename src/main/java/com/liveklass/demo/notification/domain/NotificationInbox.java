package com.liveklass.demo.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification_inbox",
        indexes = @Index(name = "idx_notification_inbox_recipient_read", columnList = "recipient_id,read_at")
)
public class NotificationInbox {

    @Id
    @Column(name = "request_id")
    private Long requestId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private NotificationRequest request;

    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationInbox(NotificationRequest request) {
        this.request = request;
        this.recipientId = request.getRecipientId();
    }

    public void initializeTimestamps(Instant now) {
        createdAt = now;
        updatedAt = now;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null || updatedAt == null) {
            initializeTimestamps(NotificationTime.now());
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = NotificationTime.now();
    }

}
