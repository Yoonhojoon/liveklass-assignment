package com.liveklass.demo.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    @Valid
    private Worker worker = new Worker();

    @Valid
    private Retry retry = new Retry();

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Worker {

        private boolean enabled = true;

        @Min(1)
        private long pollDelayMs = 5_000;

        @Min(1)
        private int batchSize = 20;

        private Duration lockTtl = Duration.ofMinutes(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollDelayMs() {
            return pollDelayMs;
        }

        public void setPollDelayMs(long pollDelayMs) {
            this.pollDelayMs = pollDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getLockTtl() {
            return lockTtl;
        }

        public void setLockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
        }
    }

    public static class Retry {

        @Min(0)
        private int maxRetries = 3;

        @NotEmpty
        private List<Duration> backoffs = new ArrayList<>(List.of(
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15)
        ));

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public List<Duration> getBackoffs() {
            return backoffs;
        }

        public void setBackoffs(List<Duration> backoffs) {
            this.backoffs = backoffs;
        }

        @AssertTrue(message = "notification.retry.backoffs must contain at least max-retries values")
        public boolean isBackoffsCoverMaxRetries() {
            return backoffs != null && backoffs.size() >= maxRetries;
        }
    }
}
