package com.liveklass.demo.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_retry_audit")
public class NotificationRetryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "operator_id", nullable = false, length = 100)
    private String operatorId;

    @Column(name = "reason", nullable = false, length = 1_000)
    private String reason;

    @Column(name = "previous_retry_count", nullable = false)
    private int previousRetryCount;

    @Column(name = "previous_failure_reason", length = 1_000)
    private String previousFailureReason;

    @Column(name = "retried_at", nullable = false)
    private Instant retriedAt;

    public NotificationRetryAudit(Long requestId, String operatorId, String reason, int previousRetryCount,
            String previousFailureReason, Instant retriedAt) {
        this.requestId = requestId;
        this.operatorId = operatorId;
        this.reason = reason;
        this.previousRetryCount = previousRetryCount;
        this.previousFailureReason = previousFailureReason;
        this.retriedAt = retriedAt;
    }

    @PrePersist
    void prePersist() {
        if (retriedAt == null) {
            retriedAt = Instant.now();
        }
    }
}
