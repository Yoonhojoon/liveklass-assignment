package com.liveklass.demo.notification.worker;

import com.liveklass.demo.notification.service.NotificationProcessingService;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.sender.NotificationSender;
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
        List<NotificationRequest> candidates = processingService.findProcessable(limit);
        int processed = 0;
        for (NotificationRequest candidate : candidates) {
            if (processOne(candidate.getId())) {
                processed++;
            }
        }
        return processed;
    }

    public boolean processOne(Long id) {
        if (!processingService.claim(id, workerId)) {
            return false;
        }
        NotificationRequest request = processingService.loadClaimed(id, workerId);
        try {
            sender.send(request);
            processingService.markSent(id, workerId);
        } catch (Exception failure) {
            processingService.recordFailure(id, workerId, failure);
        }
        return true;
    }
}
