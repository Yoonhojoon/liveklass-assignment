package com.liveklass.demo.notification.service;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.exception.NotificationNotFoundException;
import com.liveklass.demo.notification.exception.NotificationValidationException;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationInboxWithJobView;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import com.liveklass.demo.notification.service.dto.NotificationCreateCommand;
import com.liveklass.demo.notification.service.dto.NotificationCreateResult;
import com.liveklass.demo.notification.service.dto.NotificationDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationRequestService {

    private final NotificationRequestRepository requestRepository;
    private final NotificationDeliveryJobRepository deliveryJobRepository;
    private final NotificationInboxRepository inboxRepository;
    private final NotificationTemplateService templateService;
    private final Clock clock;
    private final TransactionTemplate insertTransaction;

    public NotificationRequestService(NotificationRequestRepository requestRepository,
            NotificationDeliveryJobRepository deliveryJobRepository,
            NotificationInboxRepository inboxRepository,
            NotificationTemplateService templateService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.deliveryJobRepository = deliveryJobRepository;
        this.inboxRepository = inboxRepository;
        this.templateService = templateService;
        this.clock = clock;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public NotificationCreateResult create(
            @Valid @NotNull(message = "notification create command is required") NotificationCreateCommand command
    ) {
        return requestRepository.findByRecipientIdAndNotificationTypeAndChannelAndEventId(
                        command.recipientId(), command.notificationType(), command.channel(), command.eventId())
                .map(existing -> new NotificationCreateResult(details(existing), true))
                .orElseGet(() -> saveNewOrReturnDuplicate(command, resolveContent(command)));
    }

    @Transactional(readOnly = true)
    public NotificationDetails get(Long id) {
        NotificationRequest request = requestRepository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
        return details(request);
    }

    @Transactional(readOnly = true)
    public List<NotificationDetails> listForRecipient(String recipientId, Boolean read) {
        if (isBlank(recipientId)) {
            throw new NotificationValidationException("recipientId is required");
        }
        List<NotificationInboxWithJobView> inboxes;
        if (read == null) {
            inboxes = inboxRepository.findDetailsByRecipientIdOrderByCreatedAtDesc(recipientId);
        } else if (read) {
            inboxes = inboxRepository.findReadDetailsByRecipientIdOrderByCreatedAtDesc(recipientId);
        } else {
            inboxes = inboxRepository.findUnreadDetailsByRecipientIdOrderByCreatedAtDesc(recipientId);
        }
        return inboxes.stream()
                .map(row -> details(row.request(), row.deliveryJob(), row.inbox()))
                .toList();
    }

    @Transactional
    public NotificationDetails markRead(Long id, String recipientId) {
        if (isBlank(recipientId)) {
            throw new NotificationValidationException("X-User-Id header is required");
        }
        inboxRepository.markReadIfUnread(id, recipientId, Instant.now(clock));
        NotificationInbox inbox = inboxRepository.findByRequestIdWithRequest(id).orElseThrow(() -> new NotificationNotFoundException(id));
        if (!inbox.getRecipientId().equals(recipientId)) {
            throw new NotificationNotFoundException(id);
        }
        return details(inbox.getRequest(), inbox);
    }

    private NotificationCreateResult saveNewOrReturnDuplicate(NotificationCreateCommand command, TemplateRenderResult content) {
        try {
            NotificationDetails created = insertTransaction.execute(status -> {
                NotificationRequest request = requestRepository.saveAndFlush(new NotificationRequest(
                        command.recipientId(),
                        command.notificationType(),
                        command.channel(),
                        command.eventId(),
                        content.title(),
                        content.message()
                ));
                NotificationDeliveryJob deliveryJob = deliveryJobRepository.saveAndFlush(new NotificationDeliveryJob(request, command.scheduledAt()));
                NotificationInbox inbox = inboxRepository.saveAndFlush(new NotificationInbox(request));
                return details(request, deliveryJob, inbox);
            });
            return new NotificationCreateResult(created, false);
        } catch (DataIntegrityViolationException duplicate) {
            return new NotificationCreateResult(findDuplicateAfterRollback(command, duplicate), true);
        }
    }

    private NotificationDetails findDuplicateAfterRollback(NotificationCreateCommand command, DataIntegrityViolationException duplicate) {
        return requestRepository.findByRecipientIdAndNotificationTypeAndChannelAndEventId(
                        command.recipientId(), command.notificationType(), command.channel(), command.eventId())
                .map(this::details)
                .orElseThrow(() -> duplicate);
    }

    private NotificationDetails details(NotificationRequest request) {
        NotificationInbox inbox = inboxRepository.findById(request.getId())
                .orElseThrow(() -> new NotificationNotFoundException(request.getId()));
        return details(request, inbox);
    }

    private NotificationDetails details(NotificationRequest request, NotificationInbox inbox) {
        NotificationDeliveryJob deliveryJob = deliveryJobRepository.findById(request.getId())
                .orElseThrow(() -> new NotificationNotFoundException(request.getId()));
        return details(request, deliveryJob, inbox);
    }

    private NotificationDetails details(NotificationRequest request, NotificationDeliveryJob deliveryJob, NotificationInbox inbox) {
        return NotificationDetails.from(request, deliveryJob, inbox);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private TemplateRenderResult resolveContent(NotificationCreateCommand command) {
        boolean hasDirect = !isBlank(command.title()) || !isBlank(command.message());
        boolean hasTemplate = command.templateVariables() != null && !command.templateVariables().isEmpty();
        if (hasDirect == hasTemplate) {
            throw new NotificationValidationException("provide either title/message or templateVariables");
        }
        if (hasDirect) {
            if (isBlank(command.title())) {
                throw new NotificationValidationException("title is required");
            }
            if (isBlank(command.message())) {
                throw new NotificationValidationException("message is required");
            }
            NotificationTemplateService.validateRenderedLength(command.title(), command.message());
            return new TemplateRenderResult(command.title(), command.message());
        }
        return templateService.render(command.notificationType(), command.channel(), command.templateVariables());
    }
}
