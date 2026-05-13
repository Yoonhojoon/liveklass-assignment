package com.liveklass.demo.notification.service;

import com.liveklass.demo.notification.config.NotificationProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private final NotificationProperties properties;

    public RetryPolicy(NotificationProperties properties) {
        this.properties = properties;
    }

    public int maxRetries() {
        return properties.getRetry().getMaxRetries();
    }

    public boolean retryExhaustedBeforeNextAttempt(int retryCount) {
        return retryCount >= maxRetries();
    }

    public Duration backoffForRetryCount(int retryCount) {
        if (retryCount < 1 || retryCount > maxRetries()) {
            throw new IllegalArgumentException("Unsupported retry count: " + retryCount);
        }
        return properties.getRetry().getBackoffs().get(retryCount - 1);
    }
}
