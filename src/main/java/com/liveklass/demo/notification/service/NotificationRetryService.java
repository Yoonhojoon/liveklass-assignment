package com.liveklass.demo.notification.service;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationRetryAudit;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.exception.NotificationNotFoundException;
import com.liveklass.demo.notification.exception.NotificationValidationException;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationRetryAuditRepository;
import com.liveklass.demo.notification.service.dto.NotificationDetails;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRetryService {

    private final NotificationDeliveryJobRepository deliveryJobRepository;
    private final NotificationRetryAuditRepository auditRepository;
    private final NotificationRequestService requestService;
    private final Clock clock;

    public NotificationRetryService(NotificationDeliveryJobRepository deliveryJobRepository,
            NotificationRetryAuditRepository auditRepository, NotificationRequestService requestService, Clock clock) {
        this.deliveryJobRepository = deliveryJobRepository;
        this.auditRepository = auditRepository;
        this.requestService = requestService;
        this.clock = clock;
    }

    @Transactional
    public NotificationDetails retry(Long id, String operatorId, String reason) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new NotificationValidationException("X-Operator-Id header is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new NotificationValidationException("reason is required");
        }
        NotificationDeliveryJob job = deliveryJobRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (job.getStatus() != NotificationStatus.FAILED) {
            throw new NotificationValidationException("manual retry is allowed only for FAILED notifications");
        }
        Instant now = Instant.now(clock);
        int updated = deliveryJobRepository.resetFailedForManualRetry(
                id,
                NotificationStatus.REQUESTED,
                NotificationStatus.FAILED,
                now
        );
        if (updated != 1) {
            throw new NotificationValidationException("manual retry is allowed only for FAILED notifications");
        }
        auditRepository.save(new NotificationRetryAudit(
                id,
                operatorId,
                reason,
                job.getRetryCount(),
                job.getLastFailureReason(),
                now
        ));
        return requestService.get(id);
    }
}
