package com.liveklass.demo.notification.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import com.liveklass.demo.notification.sender.NotificationSender;
import com.liveklass.demo.notification.service.NotificationProcessingService;
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
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationDeliveryJobRepository jobRepository;

    @Autowired
    private NotificationInboxRepository inboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        inboxRepository.deleteAll();
        jobRepository.deleteAll();
        requestRepository.deleteAll();
    }

    @Test
    void workerSuccessMovesRequestedDeliveryJobToSent() {
        NotificationDeliveryJob saved = job("success");
        RecordingSender sender = new RecordingSender(false);
        NotificationWorker worker = new NotificationWorker(processingService, sender);

        int processed = worker.processBatch(10);

        NotificationDeliveryJob result = jobRepository.findById(saved.getRequestId()).orElseThrow();
        assertThat(processed).isEqualTo(1);
        assertThat(sender.sentIds).containsExactly(saved.getRequestId());
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getLockedBy()).isNull();
    }

    @Test
    void retryableFailureStoresReasonAndSchedulesNextRetryOnDeliveryJob() {
        NotificationDeliveryJob saved = job("retry");
        NotificationWorker worker = new NotificationWorker(processingService, new RecordingSender(true));

        worker.processBatch(10);

        NotificationDeliveryJob result = jobRepository.findById(saved.getRequestId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.RETRY_WAITING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getLastFailureReason()).isEqualTo("temporary sender failure");
        assertThat(result.getNextRetryAt()).isNotNull();
        assertThat(result.getLockedBy()).isNull();
    }

    @Test
    void exhaustedRetryMovesDeliveryJobToFailedAndKeepsFailureReason() {
        NotificationDeliveryJob retryExhausted = job("failed");
        retryExhausted.markRetryWaiting(3, "previous", Instant.now().minusSeconds(1));
        jobRepository.saveAndFlush(retryExhausted);
        NotificationWorker worker = new NotificationWorker(processingService, new RecordingSender(true));

        worker.processBatch(10);

        NotificationDeliveryJob result = jobRepository.findById(retryExhausted.getRequestId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(3);
        assertThat(result.getLastFailureReason()).isEqualTo("temporary sender failure");
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getNextRetryAt()).isNull();
    }

    @Test
    void unclaimedWorkerDoesNotSend() {
        NotificationDeliveryJob saved = job("claimed-once");
        boolean claimed = processingService.claim(saved.getRequestId(), "worker-a");
        RecordingSender sender = new RecordingSender(false);
        NotificationWorker worker = new NotificationWorker(processingService, sender);

        boolean processed = worker.processOne(saved.getRequestId());

        assertThat(claimed).isTrue();
        assertThat(processed).isFalse();
        assertThat(sender.sentIds).isEmpty();
    }

    @Test
    void staleWorkerCannotOverwriteAfterAnotherWorkerReclaimsLock() {
        Instant now = Instant.now();
        NotificationDeliveryJob saved = job("stale-owner");
        claimInTransaction(saved.getRequestId(), "worker-a", now, now.minusSeconds(1));
        claimInTransaction(saved.getRequestId(), "worker-b", now, now.plusSeconds(600));

        boolean staleCompletion = processingService.markSent(saved.getRequestId(), "worker-a");
        boolean currentCompletion = processingService.markSent(saved.getRequestId(), "worker-b");

        NotificationDeliveryJob result = jobRepository.findById(saved.getRequestId()).orElseThrow();
        assertThat(staleCompletion).isFalse();
        assertThat(currentCompletion).isTrue();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    private void claimInTransaction(Long id, String workerId, Instant now, Instant lockedUntil) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jobRepository.claimForProcessing(
                id,
                workerId,
                lockedUntil,
                now,
                NotificationStatus.REQUESTED,
                NotificationStatus.RETRY_WAITING,
                NotificationStatus.PROCESSING
        ));
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
        NotificationDeliveryJob job = jobRepository.saveAndFlush(new NotificationDeliveryJob(request));
        inboxRepository.saveAndFlush(new NotificationInbox(request));
        return job;
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
