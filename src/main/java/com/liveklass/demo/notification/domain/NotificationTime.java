package com.liveklass.demo.notification.domain;

import java.time.Clock;
import java.time.Instant;

public final class NotificationTime {

    private static Clock clock = Clock.systemUTC();

    private NotificationTime() {
    }

    public static Instant now() {
        return clock.instant();
    }

    public static void use(Clock clock) {
        NotificationTime.clock = clock;
    }
}
