package com.liveklass.demo.notification.service;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationTemplate;
import com.liveklass.demo.notification.domain.NotificationType;
import com.liveklass.demo.notification.dto.NotificationTemplateEnabledRequest;
import com.liveklass.demo.notification.dto.NotificationTemplateUpsertRequest;
import com.liveklass.demo.notification.exception.NotificationTemplateNotFoundException;
import com.liveklass.demo.notification.exception.NotificationValidationException;
import com.liveklass.demo.notification.repository.NotificationTemplateRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTemplateService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_.-]+)}");
    private static final Pattern PLACEHOLDER_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+");
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 2_000;

    private final NotificationTemplateRepository repository;

    public NotificationTemplateService(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplate> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public NotificationTemplate get(NotificationType notificationType, NotificationChannel channel) {
        return repository.findByNotificationTypeAndChannel(notificationType, channel)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(notificationType, channel));
    }

    @Transactional
    public NotificationTemplate create(NotificationType notificationType, NotificationChannel channel,
            NotificationTemplateUpsertRequest request) {
        validateTemplateTexts(request.titleTemplate(), request.messageTemplate());
        try {
            return repository.saveAndFlush(new NotificationTemplate(
                    notificationType,
                    channel,
                    request.titleTemplate(),
                    request.messageTemplate(),
                    request.enabled()
            ));
        } catch (DataIntegrityViolationException duplicate) {
            if (repository.findByNotificationTypeAndChannel(notificationType, channel).isPresent()) {
                throw new NotificationValidationException("template already exists for " + notificationType + "/" + channel);
            }
            throw new NotificationValidationException("template could not be saved");
        }
    }

    @Transactional
    public NotificationTemplate update(NotificationType notificationType, NotificationChannel channel,
            NotificationTemplateUpsertRequest request) {
        validateTemplateTexts(request.titleTemplate(), request.messageTemplate());
        NotificationTemplate template = get(notificationType, channel);
        template.update(request.titleTemplate(), request.messageTemplate(), request.enabled());
        return template;
    }

    @Transactional
    public NotificationTemplate setEnabled(NotificationType notificationType, NotificationChannel channel,
            NotificationTemplateEnabledRequest request) {
        NotificationTemplate template = get(notificationType, channel);
        template.setEnabled(request.enabled());
        return template;
    }

    @Transactional
    public void delete(NotificationType notificationType, NotificationChannel channel) {
        repository.delete(get(notificationType, channel));
    }

    @Transactional(readOnly = true)
    public TemplateRenderResult render(NotificationType notificationType, NotificationChannel channel, Map<String, String> variables) {
        NotificationTemplate template = get(notificationType, channel);
        if (!template.isEnabled()) {
            throw new NotificationValidationException("template is disabled for " + notificationType + "/" + channel);
        }
        if (variables == null || variables.isEmpty()) {
            throw new NotificationValidationException("templateVariables are required");
        }
        String title = renderText(template.getTitleTemplate(), variables);
        String message = renderText(template.getMessageTemplate(), variables);
        validateRenderedLength(title, message);
        return new TemplateRenderResult(title, message);
    }

    private void validateTemplateTexts(String titleTemplate, String messageTemplate) {
        if (isBlank(titleTemplate)) {
            throw new NotificationValidationException("titleTemplate is required");
        }
        if (isBlank(messageTemplate)) {
            throw new NotificationValidationException("messageTemplate is required");
        }
        validatePlaceholderGrammar(titleTemplate);
        validatePlaceholderGrammar(messageTemplate);
        if (titleTemplate.length() > MAX_TITLE_LENGTH) {
            throw new NotificationValidationException("titleTemplate exceeds 200 characters");
        }
        if (messageTemplate.length() > MAX_MESSAGE_LENGTH) {
            throw new NotificationValidationException("messageTemplate exceeds 2000 characters");
        }
    }

    private void validatePlaceholderGrammar(String template) {
        int cursor = 0;
        while (cursor < template.length()) {
            int start = template.indexOf("${", cursor);
            if (start < 0) {
                return;
            }
            int end = template.indexOf('}', start + 2);
            if (end < 0) {
                throw new NotificationValidationException("template contains unsupported placeholder syntax");
            }
            String placeholderName = template.substring(start + 2, end);
            if (!PLACEHOLDER_NAME_PATTERN.matcher(placeholderName).matches()) {
                throw new NotificationValidationException("template contains unsupported placeholder syntax");
            }
            cursor = end + 1;
        }
    }

    private String renderText(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        int last = 0;
        Set<String> missing = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            rendered.append(template, last, matcher.start());
            String key = matcher.group(1);
            String value = variables.get(key);
            if (isBlank(value)) {
                missing.add(key);
            } else {
                rendered.append(value);
            }
            last = matcher.end();
        }
        rendered.append(template.substring(last));
        if (!missing.isEmpty()) {
            throw new NotificationValidationException("missing templateVariables for " + String.join(", ", missing));
        }
        return rendered.toString();
    }

    public static void validateRenderedLength(String title, String message) {
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new NotificationValidationException("rendered title exceeds 200 characters");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new NotificationValidationException("rendered message exceeds 2000 characters");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
