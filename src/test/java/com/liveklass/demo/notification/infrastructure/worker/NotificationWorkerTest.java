package com.liveklass.demo.notification.infrastructure.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.application.NotificationProcessingService;
import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.infrastructure.persistence.NotificationRequestRepository;
import com.liveklass.demo.notification.infrastructure.sender.NotificationSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "notification.worker.enabled=false")
class NotificationWorkerTest {

    @Autowired
    private NotificationProcessingService processingService;

    @Autowired
    private NotificationRequestRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void workerSuccessMovesRequestedToSent() {
        NotificationRequest saved = repository.saveAndFlush(request("success"));
        RecordingSender sender = new RecordingSender(false);
        NotificationWorker worker = new NotificationWorker(processingService, sender);

        int processed = worker.processBatch(10);

        NotificationRequest result = repository.findById(saved.getId()).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(sender.sentIds).containsExactly(saved.getId());
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    void retryableFailureStoresReasonAndSchedulesNextRetry() {
        NotificationRequest saved = repository.saveAndFlush(request("retry"));
        NotificationWorker worker = new NotificationWorker(processingService, new RecordingSender(true));

        worker.processBatch(10);

        NotificationRequest result = repository.findById(saved.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getLastFailureReason()).isEqualTo("temporary sender failure");
        assertThat(result.getNextRetryAt()).isNotNull();
        assertThat(result.getLockedBy()).isNull();
    }

    @Test
    void exhaustedRetryMovesToFailedAndKeepsFailureReason() {
        NotificationRequest retryExhausted = request("failed");
        retryExhausted.markRetryWaiting(3, "previous", Instant.now().minusSeconds(1));
        NotificationRequest saved = repository.saveAndFlush(retryExhausted);
        NotificationWorker worker = new NotificationWorker(processingService, new RecordingSender(true));

        worker.processBatch(10);

        NotificationRequest result = repository.findById(saved.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(3);
        assertThat(result.getLastFailureReason()).isEqualTo("temporary sender failure");
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getNextRetryAt()).isNull();
    }

    @Test
    void unclaimedWorkerDoesNotSend() {
        NotificationRequest saved = repository.saveAndFlush(request("claimed-once"));
        boolean claimed = processingService.claim(saved.getId(), "worker-a");
        RecordingSender sender = new RecordingSender(false);
        NotificationWorker worker = new NotificationWorker(processingService, sender);

        boolean processed = worker.processOne(saved.getId());

        assertThat(claimed).isTrue();
        assertThat(processed).isFalse();
        assertThat(sender.sentIds).isEmpty();
    }


    @Test
    void staleWorkerCannotOverwriteAfterAnotherWorkerReclaimsLock() {
        Instant now = Instant.now();
        NotificationRequest saved = repository.saveAndFlush(request("stale-owner"));
        claimInTransaction(saved.getId(), "worker-a", now, now.minusSeconds(1));
        claimInTransaction(saved.getId(), "worker-b", now, now.plusSeconds(600));

        boolean staleCompletion = processingService.markSent(saved.getId(), "worker-a");
        boolean currentCompletion = processingService.markSent(saved.getId(), "worker-b");

        NotificationRequest result = repository.findById(saved.getId()).orElseThrow();
        assertThat(staleCompletion).isFalse();
        assertThat(currentCompletion).isTrue();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    private void claimInTransaction(Long id, String workerId, Instant now, Instant lockedUntil) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.claimForProcessing(
                id,
                workerId,
                lockedUntil,
                now,
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING
        ));
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

    private static class RecordingSender implements NotificationSender {
        private final boolean fail;
        private final List<Long> sentIds = new ArrayList<>();

        private RecordingSender(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void send(NotificationRequest request) {
            if (fail) {
                throw new IllegalStateException("temporary sender failure");
            }
            sentIds.add(request.getId());
        }
    }
}
