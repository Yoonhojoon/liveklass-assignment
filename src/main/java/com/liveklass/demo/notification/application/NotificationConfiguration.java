package com.liveklass.demo.notification.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
