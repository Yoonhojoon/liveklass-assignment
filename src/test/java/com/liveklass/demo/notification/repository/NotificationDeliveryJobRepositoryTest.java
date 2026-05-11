package com.liveklass.demo.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class NotificationDeliveryJobRepositoryTest {

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationDeliveryJobRepository jobRepository;

    @Test
    void conditionalClaimAllowsOnlyOneWorker() {
        NotificationDeliveryJob saved = job("event-claim");
        Instant now = Instant.now();

        int first = claim(saved.getRequestId(), "worker-a", now, now.plusSeconds(600));
        int second = claim(saved.getRequestId(), "worker-b", now, now.plusSeconds(600));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        NotificationDeliveryJob claimed = jobRepository.findById(saved.getRequestId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(claimed.getLockedBy()).isEqualTo("worker-a");
    }

    @Test
    void processableRowsIncludeRequestedDueRetryAndStaleProcessingOnly() {
        Instant now = Instant.now();
        NotificationDeliveryJob requested = job("requested");
        NotificationDeliveryJob dueRetry = job("due-retry");
        dueRetry.markRetryWaiting(1, "temporary", now.minusSeconds(1));
        jobRepository.save(dueRetry);
        NotificationDeliveryJob nonDueRetry = job("non-due-retry");
        nonDueRetry.markRetryWaiting(1, "temporary", now.plusSeconds(60));
        jobRepository.save(nonDueRetry);
        NotificationDeliveryJob stale = job("stale");
        claim(stale.getRequestId(), "worker-stale", now, now.minusSeconds(1));
        NotificationDeliveryJob fresh = job("fresh");
        claim(fresh.getRequestId(), "worker-fresh", now, now.plusSeconds(600));
        NotificationDeliveryJob sent = job("sent");
        claim(sent.getRequestId(), "worker-sent", now, now.plusSeconds(600));
        jobRepository.findById(sent.getRequestId()).orElseThrow().markSent(now);
        NotificationDeliveryJob failed = job("failed");
        claim(failed.getRequestId(), "worker-failed", now, now.plusSeconds(600));
        jobRepository.findById(failed.getRequestId()).orElseThrow().markFailed(3, "failed", now);
        jobRepository.flush();

        List<Long> processableIds = jobRepository.findProcessable(
                        now,
                        NotificationStatus.REQUESTED,
                        NotificationStatus.RETRY_WAITING,
                        NotificationStatus.PROCESSING,
                        PageRequest.of(0, 10)
                ).stream()
                .map(NotificationDeliveryJob::getRequestId)
                .toList();

        assertThat(processableIds).contains(requested.getRequestId(), dueRetry.getRequestId(), stale.getRequestId());
        assertThat(processableIds).doesNotContain(
                nonDueRetry.getRequestId(), fresh.getRequestId(), sent.getRequestId(), failed.getRequestId());
    }

    private NotificationDeliveryJob job(String eventId) {
        NotificationRequest request = requestRepository.saveAndFlush(new NotificationRequest(
                "user-1",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                eventId,
                "title",
                "message"
        ));
        return jobRepository.saveAndFlush(new NotificationDeliveryJob(request));
    }

    private int claim(Long requestId, String workerId, Instant now, Instant lockedUntil) {
        return jobRepository.claimForProcessing(
                requestId,
                workerId,
                lockedUntil,
                now,
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING
        );
    }
}
