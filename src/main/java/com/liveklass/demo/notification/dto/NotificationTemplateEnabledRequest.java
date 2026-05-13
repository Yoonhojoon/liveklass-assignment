package com.liveklass.demo.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationTemplateEnabledRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
