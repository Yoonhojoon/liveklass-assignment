package com.liveklass.demo.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class NotificationRequestRepositoryTest {

    @Autowired
    private NotificationRequestRepository repository;

    @Test
    void uniqueConstraintProtectsDuplicateEventIdentity() {
        repository.saveAndFlush(request("event-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(request("event-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conditionalClaimAllowsOnlyOneWorker() {
        NotificationRequest saved = repository.saveAndFlush(request("event-claim"));
        Instant now = Instant.now();

        int first = claim(saved.getId(), "worker-a", now, now.plusSeconds(600));
        int second = claim(saved.getId(), "worker-b", now, now.plusSeconds(600));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        NotificationRequest claimed = repository.findById(saved.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(claimed.getLockedBy()).isEqualTo("worker-a");
    }

    @Test
    void processableRowsIncludeRequestedDueRetryAndStaleProcessingOnly() {
        Instant now = Instant.now();
        NotificationRequest requested = repository.save(request("requested"));
        NotificationRequest dueRetry = request("due-retry");
        dueRetry.markRetryWaiting(1, "temporary", now.minusSeconds(1));
        repository.save(dueRetry);
        NotificationRequest nonDueRetry = request("non-due-retry");
        nonDueRetry.markRetryWaiting(1, "temporary", now.plusSeconds(60));
        repository.save(nonDueRetry);
        NotificationRequest stale = repository.saveAndFlush(request("stale"));
        claim(stale.getId(), "worker-stale", now, now.minusSeconds(1));
        NotificationRequest fresh = repository.saveAndFlush(request("fresh"));
        claim(fresh.getId(), "worker-fresh", now, now.plusSeconds(600));

        List<Long> processableIds = repository.findProcessable(
                        now,
                        NotificationStatus.REQUESTED,
                        NotificationStatus.RETRY_WAITING,
                        NotificationStatus.PROCESSING,
                        PageRequest.of(0, 10)
                ).stream()
                .map(NotificationRequest::getId)
                .toList();

        assertThat(processableIds).contains(requested.getId(), dueRetry.getId(), stale.getId());
        assertThat(processableIds).doesNotContain(nonDueRetry.getId(), fresh.getId());
    }

    private int claim(Long id, String workerId, Instant now, Instant lockedUntil) {
        return repository.claimForProcessing(
                id,
                workerId,
                lockedUntil,
                now,
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING
        );
    }

    private NotificationRequest request(String eventId) {
        return new NotificationRequest(
                "user-1",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                eventId,
                "title",
                "message"
        );
    }
}
