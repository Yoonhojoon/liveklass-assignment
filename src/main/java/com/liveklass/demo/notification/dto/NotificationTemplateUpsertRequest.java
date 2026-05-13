package com.liveklass.demo.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationTemplateUpsertRequest(
        @NotBlank(message = "titleTemplate is required")
        String titleTemplate,
        @NotBlank(message = "messageTemplate is required")
        String messageTemplate,
        boolean enabled
) {
}
