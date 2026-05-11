package com.liveklass.demo.notification.service;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.exception.NotificationNotFoundException;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationProcessingService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final NotificationDeliveryJobRepository repository;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public NotificationProcessingService(NotificationDeliveryJobRepository repository, RetryPolicy retryPolicy, Clock clock) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryJob> findProcessable(int limit) {
        return repository.findProcessable(
                Instant.now(clock),
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING,
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public boolean claim(Long requestId, String workerId) {
        Instant now = Instant.now(clock);
        int updated = repository.claimForProcessing(
                requestId,
                workerId,
                now.plus(LOCK_TTL),
                now,
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING
        );
        return updated == 1;
    }

    @Transactional(readOnly = true)
    public NotificationDeliveryJob loadClaimed(Long requestId, String workerId) {
        return repository.findClaimedWithRequest(requestId, NotificationStatus.PROCESSING, workerId)
                .orElseThrow(() -> new NotificationNotFoundException(requestId));
    }

    @Transactional
    public boolean markSent(Long requestId, String workerId) {
        return repository.findByRequestIdAndStatusAndLockedBy(requestId, NotificationStatus.PROCESSING, workerId)
                .map(job -> {
                    job.markSent(Instant.now(clock));
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean recordFailure(Long requestId, String workerId, Exception failure) {
        return repository.findByRequestIdAndStatusAndLockedBy(requestId, NotificationStatus.PROCESSING, workerId)
                .map(job -> recordOwnedFailure(job, failure))
                .orElse(false);
    }

    private boolean recordOwnedFailure(NotificationDeliveryJob job, Exception failure) {
        String reason = trimFailureReason(failure);
        Instant now = Instant.now(clock);
        if (retryPolicy.retryExhaustedBeforeNextAttempt(job.getRetryCount())) {
            job.markFailed(job.getRetryCount(), reason, now);
            return true;
        }
        int nextRetryCount = job.getRetryCount() + 1;
        job.markRetryWaiting(nextRetryCount, reason, now.plus(retryPolicy.backoffForRetryCount(nextRetryCount)));
        return true;
    }

    private String trimFailureReason(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        if (message.length() > 1_000) {
            return message.substring(0, 1_000);
        }
        return message;
    }
}
