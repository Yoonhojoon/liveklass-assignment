package com.liveklass.demo.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationRetryRequest(
        @NotBlank(message = "reason is required")
        String reason
) {
}
