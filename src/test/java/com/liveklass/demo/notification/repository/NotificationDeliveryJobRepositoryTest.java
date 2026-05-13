package com.liveklass.demo.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DisplayName("알림 발송 작업 저장소 테스트")
@DataJpaTest
class NotificationDeliveryJobRepositoryTest {

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationDeliveryJobRepository jobRepository;

    @Nested
    @DisplayName("작업 점유")
    class Claim {

        @Test
        @DisplayName("조건부 업데이트로 한 워커만 점유한다")
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
        @DisplayName("수동 재시도 reset은 FAILED 작업에만 적용된다")
        void manualRetryResetOnlyAffectsFailedRows() {
            Instant now = Instant.now();
            NotificationDeliveryJob failed = job("failed-reset");
            claim(failed.getRequestId(), "worker-a", now, now.plusSeconds(600));
            jobRepository.findById(failed.getRequestId()).orElseThrow().markFailed(3, "smtp down", now);
            NotificationDeliveryJob requested = job("requested-reset");
            jobRepository.flush();

            int failedUpdated = jobRepository.resetFailedForManualRetry(
                    failed.getRequestId(),
                    NotificationStatus.REQUESTED,
                    NotificationStatus.FAILED,
                    now
            );
            int requestedUpdated = jobRepository.resetFailedForManualRetry(
                    requested.getRequestId(),
                    NotificationStatus.REQUESTED,
                    NotificationStatus.FAILED,
                    now
            );

            NotificationDeliveryJob reset = jobRepository.findById(failed.getRequestId()).orElseThrow();
            assertThat(failedUpdated).isEqualTo(1);
            assertThat(requestedUpdated).isZero();
            assertThat(reset.getStatus()).isEqualTo(NotificationStatus.REQUESTED);
            assertThat(reset.getRetryCount()).isZero();
            assertThat(reset.getLastFailureReason()).isNull();
        }
    }

    @Nested
    @DisplayName("처리 대상 조회")
    class ProcessableLookup {

        @Test
        @DisplayName("요청됨, 재시도 도래, 오래된 처리중 작업만 반환한다")
        void processableRowsIncludeRequestedDueRetryAndStaleProcessingOnly() {
            Instant now = Instant.now();
            NotificationDeliveryJob requested = job("requested");
            NotificationDeliveryJob dueRetry = job("due-retry");
            dueRetry.markRetryWaiting(1, "temporary", now.minusSeconds(1), now);
            jobRepository.save(dueRetry);
            NotificationDeliveryJob nonDueRetry = job("non-due-retry");
            nonDueRetry.markRetryWaiting(1, "temporary", now.plusSeconds(60), now);
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

        @Test
        @DisplayName("예약 미래 시각 요청은 처리 대상에서 제외한다")
        void futureScheduledRowsAreNotProcessableUntilDue() {
            Instant now = Instant.now();
            NotificationRequest request = requestRepository.saveAndFlush(new NotificationRequest(
                    "user-1",
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "scheduled-future",
                    "title",
                    "message"
            ));
            NotificationDeliveryJob futureScheduled = jobRepository.saveAndFlush(new NotificationDeliveryJob(
                    request,
                    now.plusSeconds(300)
            ));

            List<Long> processableIds = jobRepository.findProcessable(
                            now,
                            NotificationStatus.REQUESTED,
                            NotificationStatus.RETRY_WAITING,
                            NotificationStatus.PROCESSING,
                            PageRequest.of(0, 10)
                    ).stream()
                    .map(NotificationDeliveryJob::getRequestId)
                    .toList();

            assertThat(processableIds).doesNotContain(futureScheduled.getRequestId());
            assertThat(claim(futureScheduled.getRequestId(), "worker-a", now, now.plusSeconds(600))).isZero();
        }
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
