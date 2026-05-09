package com.liveklass.demo.notification.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private static final int MAX_RETRIES = 3;

    public int maxRetries() {
        return MAX_RETRIES;
    }

    public boolean retryExhaustedBeforeNextAttempt(int retryCount) {
        return retryCount >= MAX_RETRIES;
    }

    public Duration backoffForRetryCount(int retryCount) {
        return switch (retryCount) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(15);
            default -> throw new IllegalArgumentException("Unsupported retry count: " + retryCount);
        };
    }
}
