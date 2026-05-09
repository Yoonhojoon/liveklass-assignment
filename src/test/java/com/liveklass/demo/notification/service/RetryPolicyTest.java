package com.liveklass.demo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Test
    void maxRetriesMeansThreeRetryOpportunitiesAfterInitialAttempt() {
        assertThat(retryPolicy.maxRetries()).isEqualTo(3);
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(0)).isFalse();
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(1)).isFalse();
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(2)).isFalse();
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(3)).isTrue();
    }

    @Test
    void retryBackoffUsesOneFiveFifteenMinutes() {
        assertThat(retryPolicy.backoffForRetryCount(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryPolicy.backoffForRetryCount(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(retryPolicy.backoffForRetryCount(3)).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(() -> retryPolicy.backoffForRetryCount(4)).isInstanceOf(IllegalArgumentException.class);
    }
}
