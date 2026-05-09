package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationChannel;
import com.liveklass.demo.notification.domain.NotificationRequest;
import com.liveklass.demo.notification.domain.NotificationStatus;
import com.liveklass.demo.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, Long> {

    Optional<NotificationRequest> findByRecipientIdAndNotificationTypeAndChannelAndEventId(
            String recipientId,
            NotificationType notificationType,
            NotificationChannel channel,
            String eventId
    );

    List<NotificationRequest> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<NotificationRequest> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(String recipientId);

    List<NotificationRequest> findByRecipientIdAndReadAtIsNotNullOrderByCreatedAtDesc(String recipientId);

    @Query("""
            select n
            from NotificationRequest n
            where n.status = :requested
               or (n.status = :retryWaiting and n.nextRetryAt <= :now)
               or (n.status = :processing and n.lockedUntil < :now)
            order by n.createdAt asc
            """)
    List<NotificationRequest> findProcessable(
            @Param("now") Instant now,
            @Param("requested") NotificationStatus requested,
            @Param("retryWaiting") NotificationStatus retryWaiting,
            @Param("processing") NotificationStatus processing,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationRequest n
               set n.status = :processing,
                   n.lockedBy = :workerId,
                   n.lockedUntil = :lockedUntil,
                   n.processingStartedAt = :now,
                   n.updatedAt = :now
             where n.id = :id
               and (
                    n.status = :requested
                    or (n.status = :retryWaiting and n.nextRetryAt <= :now)
                    or (n.status = :processing and n.lockedUntil < :now)
               )
            """)
    int claimForProcessing(
            @Param("id") Long id,
            @Param("workerId") String workerId,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now,
            @Param("requested") NotificationStatus requested,
            @Param("retryWaiting") NotificationStatus retryWaiting,
            @Param("processing") NotificationStatus processing
    );

    Optional<NotificationRequest> findByIdAndStatusAndLockedBy(Long id, NotificationStatus status, String lockedBy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationRequest n
               set n.readAt = :now,
                   n.updatedAt = :now
             where n.id = :id
               and n.recipientId = :recipientId
               and n.readAt is null
            """)
    int markReadIfUnread(@Param("id") Long id, @Param("recipientId") String recipientId, @Param("now") Instant now);
}
