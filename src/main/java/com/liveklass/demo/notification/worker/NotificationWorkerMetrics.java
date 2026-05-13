package com.liveklass.demo.notification.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorkerMetrics {

    private final Counter sent;
    private final Counter retryScheduled;
    private final Counter failed;
    private final Counter claimSkipped;

    public NotificationWorkerMetrics(MeterRegistry meterRegistry) {
        this.sent = meterRegistry.counter("notification.worker.sent");
        this.retryScheduled = meterRegistry.counter("notification.worker.retry_scheduled");
        this.failed = meterRegistry.counter("notification.worker.failed");
        this.claimSkipped = meterRegistry.counter("notification.worker.claim_skipped");
    }

    void sent() {
        sent.increment();
    }

    void retryScheduled() {
        retryScheduled.increment();
    }

    void failed() {
        failed.increment();
    }

    void claimSkipped() {
        claimSkipped.increment();
    }
}
