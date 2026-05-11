package com.liveklass.demo.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Index;
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
        name = "notification_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_event",
                columnNames = {"recipient_id", "notification_type", "channel", "event_id"}
        ),
        indexes = {
                @Index(name = "idx_notification_status_retry", columnList = "status,next_retry_at"),
                @Index(name = "idx_notification_status_lock", columnList = "status,locked_until"),
                @Index(name = "idx_notification_recipient_read", columnList = "recipient_id,read_at")
        }
)
public class NotificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 2_000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationStatus status = NotificationStatus.REQUESTED;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_failure_reason", length = 1_000)
    private String lastFailureReason;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    public NotificationRequest(String recipientId, NotificationType notificationType, NotificationChannel channel,
            String eventId, String title, String message) {
        this.recipientId = recipientId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.eventId = eventId;
        this.title = title;
        this.message = message;
        this.status = NotificationStatus.REQUESTED;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markSent(Instant now) {
        status = NotificationStatus.SENT;
        sentAt = now;
        failedAt = null;
        nextRetryAt = null;
        lastFailureReason = null;
        clearLock();
    }

    public void markRetryWaiting(int nextRetryCount, String failureReason, Instant nextRetryAt) {
        status = NotificationStatus.RETRY_WAITING;
        retryCount = nextRetryCount;
        lastFailureReason = failureReason;
        this.nextRetryAt = nextRetryAt;
        clearLock();
    }

    public void markFailed(int nextRetryCount, String failureReason, Instant now) {
        status = NotificationStatus.FAILED;
        retryCount = nextRetryCount;
        lastFailureReason = failureReason;
        failedAt = now;
        nextRetryAt = null;
        clearLock();
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    private void clearLock() {
        lockedBy = null;
        lockedUntil = null;
    }

}
