package com.liveklass.demo.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "notification_delivery_job",
        indexes = {
                @Index(name = "idx_notification_job_status_retry", columnList = "status,next_retry_at"),
                @Index(name = "idx_notification_job_status_lock", columnList = "status,locked_until"),
                @Index(name = "idx_notification_job_status_scheduled", columnList = "status,scheduled_at")
        }
)
public class NotificationDeliveryJob {

    @Id
    @Column(name = "request_id")
    private Long requestId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private NotificationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationStatus status = NotificationStatus.REQUESTED;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_failure_reason", length = 1_000)
    private String lastFailureReason;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationDeliveryJob(NotificationRequest request) {
        this.request = request;
    }

    public NotificationDeliveryJob(NotificationRequest request, Instant scheduledAt) {
        this.request = request;
        this.scheduledAt = scheduledAt;
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

    public void markSent(Instant now) {
        status = NotificationStatus.SENT;
        sentAt = now;
        failedAt = null;
        nextRetryAt = null;
        lastFailureReason = null;
        updatedAt = now;
        clearLock();
    }

    public void markRetryWaiting(int nextRetryCount, String failureReason, Instant nextRetryAt, Instant now) {
        status = NotificationStatus.RETRY_WAITING;
        retryCount = nextRetryCount;
        lastFailureReason = failureReason;
        this.nextRetryAt = nextRetryAt;
        updatedAt = now;
        clearLock();
    }

    public void markFailed(int nextRetryCount, String failureReason, Instant now) {
        status = NotificationStatus.FAILED;
        retryCount = nextRetryCount;
        lastFailureReason = failureReason;
        failedAt = now;
        nextRetryAt = null;
        updatedAt = now;
        clearLock();
    }

    private void clearLock() {
        lockedBy = null;
        lockedUntil = null;
    }
}
