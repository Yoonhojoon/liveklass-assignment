package com.liveklass.demo.notification.worker;

import com.liveklass.demo.notification.config.NotificationProperties;
import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.sender.NotificationSendCommand;
import com.liveklass.demo.notification.sender.NotificationSender;
import com.liveklass.demo.notification.service.NotificationProcessingService;
import com.liveklass.demo.notification.service.NotificationProcessingService.FailureHandlingResult;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {

    private final NotificationProcessingService processingService;
    private final NotificationSender sender;
    private final NotificationProperties properties;
    private final NotificationWorkerMetrics metrics;
    private final String workerId = "worker-" + UUID.randomUUID();

    public NotificationWorker(NotificationProcessingService processingService, NotificationSender sender,
            NotificationProperties properties, NotificationWorkerMetrics metrics) {
        this.processingService = processingService;
        this.sender = sender;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${notification.worker.poll-delay-ms:${notification.worker.pollDelayMs:5000}}")
    public void poll() {
        int batchSize = properties.getWorker().getBatchSize();
        log.debug("[NOTIFICATION][WORKER] notification worker poll started workerId={} batchSize={}", workerId, batchSize);
        int processed = processBatch(batchSize);
        log.debug("[NOTIFICATION][WORKER] notification worker poll finished workerId={} processed={}", workerId, processed);
    }

    public int processBatch(int limit) {
        List<NotificationDeliveryJob> candidates = processingService.findProcessable(limit);
        log.debug("[NOTIFICATION][WORKER] notification worker candidates loaded workerId={} candidates={}", workerId, candidates.size());
        int processed = 0;
        for (NotificationDeliveryJob candidate : candidates) {
            if (processOne(candidate.getRequestId())) {
                processed++;
            }
        }
        return processed;
    }

    public boolean processOne(Long requestId) {
        if (!processingService.claim(requestId, workerId)) {
            metrics.claimSkipped();
            log.info("[NOTIFICATION][WORKER] notification worker claim skipped workerId={} requestId={}", workerId, requestId);
            return false;
        }
        NotificationDeliveryJob job = processingService.loadClaimed(requestId, workerId);
        try {
            sender.send(NotificationSendCommand.from(job.getRequest()));
            if (processingService.markSent(requestId, workerId)) {
                metrics.sent();
                log.info("[NOTIFICATION][WORKER] notification sent workerId={} requestId={}", workerId, requestId);
            }
        } catch (Exception failure) {
            FailureHandlingResult result = processingService.recordFailure(requestId, workerId, failure);
            if (result == FailureHandlingResult.RETRY_SCHEDULED) {
                metrics.retryScheduled();
                log.warn("[NOTIFICATION][WORKER] notification retry scheduled workerId={} requestId={} reason={}",
                        workerId, requestId, failure.getMessage());
            } else if (result == FailureHandlingResult.FAILED) {
                metrics.failed();
                log.error("[NOTIFICATION][WORKER] notification finally failed workerId={} requestId={} reason={}",
                        workerId, requestId, failure.getMessage());
            }
        }
        return true;
    }
}
