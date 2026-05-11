package com.liveklass.demo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("재시도 정책 테스트")
class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy();

    @Test
    @DisplayName("최대 세 번까지 재시도 기회를 제공한다")
    void maxRetriesMeansThreeRetryOpportunitiesAfterInitialAttempt() {
        assertThat(retryPolicy.maxRetries()).isEqualTo(3);
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(0)).isFalse();
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(1)).isFalse();
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(2)).isFalse();
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(3)).isTrue();
    }

    @Test
    @DisplayName("재시도 횟수별 백오프는 1분, 5분, 15분이다")
    void retryBackoffUsesOneFiveFifteenMinutes() {
        assertThat(retryPolicy.backoffForRetryCount(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryPolicy.backoffForRetryCount(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(retryPolicy.backoffForRetryCount(3)).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(() -> retryPolicy.backoffForRetryCount(4)).isInstanceOf(IllegalArgumentException.class);
    }
}
