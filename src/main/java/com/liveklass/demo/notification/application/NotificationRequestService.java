package com.liveklass.demo.notification.application;

import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.infrastructure.persistence.NotificationRequestRepository;
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

    private final NotificationRequestRepository repository;
    private final Clock clock;
    private final TransactionTemplate insertTransaction;

    public NotificationRequestService(NotificationRequestRepository repository, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public NotificationCreateResult create(NotificationCreateCommand command) {
        validate(command);
        return repository.findByRecipientIdAndNotificationTypeAndChannelAndEventId(
                        command.recipientId(), command.notificationType(), command.channel(), command.eventId())
                .map(existing -> new NotificationCreateResult(existing, true))
                .orElseGet(() -> saveNewOrReturnDuplicate(command));
    }

    @Transactional(readOnly = true)
    public NotificationRequest get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<NotificationRequest> listForRecipient(String recipientId, Boolean read) {
        if (isBlank(recipientId)) {
            throw new NotificationValidationException("recipientId is required");
        }
        if (read == null) {
            return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        }
        if (read) {
            return repository.findByRecipientIdAndReadAtIsNotNullOrderByCreatedAtDesc(recipientId);
        }
        return repository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId);
    }

    @Transactional
    public NotificationRequest markRead(Long id, String recipientId) {
        if (isBlank(recipientId)) {
            throw new NotificationValidationException("X-User-Id header is required");
        }
        repository.markReadIfUnread(id, recipientId, Instant.now(clock));
        NotificationRequest notification = get(id);
        if (!notification.getRecipientId().equals(recipientId)) {
            throw new NotificationNotFoundException(id);
        }
        return notification;
    }

    private NotificationCreateResult saveNewOrReturnDuplicate(NotificationCreateCommand command) {
        try {
            NotificationRequest created = insertTransaction.execute(status -> repository.saveAndFlush(new NotificationRequest(
                    command.recipientId(),
                    command.notificationType(),
                    command.channel(),
                    command.eventId(),
                    command.title(),
                    command.message()
            )));
            return new NotificationCreateResult(created, false);
        } catch (DataIntegrityViolationException duplicate) {
            return new NotificationCreateResult(findDuplicateAfterRollback(command, duplicate), true);
        }
    }

    private NotificationRequest findDuplicateAfterRollback(NotificationCreateCommand command, DataIntegrityViolationException duplicate) {
        return repository.findByRecipientIdAndNotificationTypeAndChannelAndEventId(
                        command.recipientId(), command.notificationType(), command.channel(), command.eventId())
                .orElseThrow(() -> duplicate);
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
