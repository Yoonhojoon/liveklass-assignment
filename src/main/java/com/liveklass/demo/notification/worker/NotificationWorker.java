package com.liveklass.demo.notification.worker;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.sender.NotificationSender;
import com.liveklass.demo.notification.service.NotificationProcessingService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {

    private final NotificationProcessingService processingService;
    private final NotificationSender sender;
    private final String workerId = "worker-" + UUID.randomUUID();

    public NotificationWorker(NotificationProcessingService processingService, NotificationSender sender) {
        this.processingService = processingService;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${notification.worker.poll-delay-ms:5000}")
    public void poll() {
        processBatch(20);
    }

    public int processBatch(int limit) {
        List<NotificationDeliveryJob> candidates = processingService.findProcessable(limit);
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
            return false;
        }
        NotificationDeliveryJob job = processingService.loadClaimed(requestId, workerId);
        try {
            sender.send(job.getRequest());
            processingService.markSent(requestId, workerId);
        } catch (Exception failure) {
            processingService.recordFailure(requestId, workerId, failure);
        }
        return true;
    }
}
