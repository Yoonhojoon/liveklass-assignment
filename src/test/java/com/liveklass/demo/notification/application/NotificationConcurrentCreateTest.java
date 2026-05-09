package com.liveklass.demo.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.infrastructure.persistence.NotificationRequestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "notification.worker.enabled=false")
class NotificationConcurrentCreateTest {

    @Autowired
    private NotificationRequestService service;

    @Autowired
    private NotificationRequestRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void concurrentDuplicateCreateReturnsSameExistingRequest() throws Exception {
        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<NotificationCreateResult>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.create(command());
            });
        }

        List<Future<NotificationCreateResult>> futures = tasks.stream()
                .map(executor::submit)
                .toList();
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<NotificationCreateResult> results = new ArrayList<>();
        for (Future<NotificationCreateResult> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        Set<Long> ids = results.stream()
                .map(result -> result.notificationRequest().getId())
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(results).anyMatch(result -> !result.duplicated());
        assertThat(results.stream().filter(NotificationCreateResult::duplicated).count()).isEqualTo(attempts - 1);
    }

    private NotificationCreateCommand command() {
        return new NotificationCreateCommand(
                "user-concurrent",
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                "event-concurrent",
                "title",
                "message"
        );
    }
}
