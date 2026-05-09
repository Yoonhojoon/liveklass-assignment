package com.liveklass.demo.notification.application;

import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.infrastructure.persistence.NotificationRequestRepository;
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

    private final NotificationRequestRepository repository;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public NotificationProcessingService(NotificationRequestRepository repository, RetryPolicy retryPolicy, Clock clock) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationRequest> findProcessable(int limit) {
        return repository.findProcessable(
                Instant.now(clock),
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING,
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public boolean claim(Long id, String workerId) {
        Instant now = Instant.now(clock);
        int updated = repository.claimForProcessing(
                id,
                workerId,
                now.plus(LOCK_TTL),
                now,
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING
        );
        return updated == 1;
    }

    @Transactional
    public NotificationRequest loadClaimed(Long id, String workerId) {
        return repository.findByIdAndStatusAndLockedBy(id, NotificationStatus.PROCESSING, workerId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional
    public boolean markSent(Long id, String workerId) {
        return repository.findByIdAndStatusAndLockedBy(id, NotificationStatus.PROCESSING, workerId)
                .map(request -> {
                    request.markSent(Instant.now(clock));
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean recordFailure(Long id, String workerId, Exception failure) {
        return repository.findByIdAndStatusAndLockedBy(id, NotificationStatus.PROCESSING, workerId)
                .map(request -> recordOwnedFailure(request, failure))
                .orElse(false);
    }

    private boolean recordOwnedFailure(NotificationRequest request, Exception failure) {
        String reason = trimFailureReason(failure);
        Instant now = Instant.now(clock);
        if (retryPolicy.retryExhaustedBeforeNextAttempt(request.getRetryCount())) {
            request.markFailed(request.getRetryCount(), reason, now);
            return true;
        }
        int nextRetryCount = request.getRetryCount() + 1;
        request.markRetryWaiting(nextRetryCount, reason, now.plus(retryPolicy.backoffForRetryCount(nextRetryCount)));
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
