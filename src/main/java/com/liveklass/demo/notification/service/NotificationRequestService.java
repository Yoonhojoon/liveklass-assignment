package com.liveklass.demo.notification.service;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationInbox;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.repository.NotificationDeliveryJobRepository;
import com.liveklass.demo.notification.repository.NotificationInboxRepository;
import com.liveklass.demo.notification.repository.NotificationRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationRequestService {

    private final NotificationRequestRepository requestRepository;
    private final NotificationDeliveryJobRepository deliveryJobRepository;
    private final NotificationInboxRepository inboxRepository;
    private final Clock clock;
    private final TransactionTemplate insertTransaction;

    public NotificationRequestService(NotificationRequestRepository requestRepository,
            NotificationDeliveryJobRepository deliveryJobRepository,
            NotificationInboxRepository inboxRepository,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.deliveryJobRepository = deliveryJobRepository;
        this.inboxRepository = inboxRepository;
        this.clock = clock;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public NotificationCreateResult create(NotificationCreateCommand command) {
        validate(command);
        return requestRepository.findByRecipientIdAndNotificationTypeAndChannelAndEventId(
                        command.recipientId(), command.notificationType(), command.channel(), command.eventId())
                .map(existing -> new NotificationCreateResult(details(existing), true))
                .orElseGet(() -> saveNewOrReturnDuplicate(command));
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
        List<NotificationInbox> inboxes;
        if (read == null) {
            inboxes = inboxRepository.findByRecipientIdWithRequestOrderByCreatedAtDesc(recipientId);
        } else if (read) {
            inboxes = inboxRepository.findReadByRecipientIdWithRequestOrderByCreatedAtDesc(recipientId);
        } else {
            inboxes = inboxRepository.findUnreadByRecipientIdWithRequestOrderByCreatedAtDesc(recipientId);
        }
        return inboxes.stream()
                .map(inbox -> details(inbox.getRequest(), inbox))
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

    private NotificationCreateResult saveNewOrReturnDuplicate(NotificationCreateCommand command) {
        try {
            NotificationDetails created = insertTransaction.execute(status -> {
                NotificationRequest request = requestRepository.saveAndFlush(new NotificationRequest(
                        command.recipientId(),
                        command.notificationType(),
                        command.channel(),
                        command.eventId(),
                        command.title(),
                        command.message()
                ));
                NotificationDeliveryJob deliveryJob = deliveryJobRepository.saveAndFlush(new NotificationDeliveryJob(request));
                NotificationInbox inbox = inboxRepository.saveAndFlush(new NotificationInbox(request));
                return new NotificationDetails(request, deliveryJob, inbox);
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
        return new NotificationDetails(request, deliveryJob, inbox);
    }

    private void validate(NotificationCreateCommand command) {
        if (command.notificationType() == null) {
            throw new NotificationValidationException("notificationType is required");
        }
        if (command.channel() == null) {
            throw new NotificationValidationException("channel is required");
        }
        if (isBlank(command.recipientId())) {
            throw new NotificationValidationException("recipientId is required");
        }
        if (isBlank(command.eventId())) {
            throw new NotificationValidationException("eventId is required");
        }
        if (isBlank(command.title())) {
            throw new NotificationValidationException("title is required");
        }
        if (isBlank(command.message())) {
            throw new NotificationValidationException("message is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
