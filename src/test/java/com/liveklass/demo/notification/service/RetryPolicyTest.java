package com.liveklass.demo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.demo.notification.config.NotificationProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("재시도 정책 테스트")
class RetryPolicyTest {

    private final NotificationProperties properties = new NotificationProperties();
    private final RetryPolicy retryPolicy = new RetryPolicy(properties);

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

    @Test
    @DisplayName("외부 설정으로 최대 재시도와 백오프를 변경할 수 있다")
    void retryPolicyUsesExternalProperties() {
        properties.getRetry().setMaxRetries(2);
        properties.getRetry().setBackoffs(List.of(Duration.ofSeconds(10), Duration.ofSeconds(30)));

        assertThat(retryPolicy.maxRetries()).isEqualTo(2);
        assertThat(retryPolicy.backoffForRetryCount(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(retryPolicy.backoffForRetryCount(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(retryPolicy.retryExhaustedBeforeNextAttempt(2)).isTrue();
    }
}
