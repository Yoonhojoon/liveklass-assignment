package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationDeliveryJob;
import com.liveklass.demo.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryJobRepository extends JpaRepository<NotificationDeliveryJob, Long> {

    @Query("""
            select j
            from NotificationDeliveryJob j
            where j.status = :requested
               or (j.status = :retryWaiting and j.nextRetryAt <= :now)
               or (j.status = :processing and j.lockedUntil < :now)
            order by j.createdAt asc
            """)
    List<NotificationDeliveryJob> findProcessable(
            @Param("now") Instant now,
            @Param("requested") NotificationStatus requested,
            @Param("retryWaiting") NotificationStatus retryWaiting,
            @Param("processing") NotificationStatus processing,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDeliveryJob j
               set j.status = :processing,
                   j.lockedBy = :workerId,
                   j.lockedUntil = :lockedUntil,
                   j.processingStartedAt = :now,
                   j.updatedAt = :now
             where j.requestId = :requestId
               and (
                    j.status = :requested
                    or (j.status = :retryWaiting and j.nextRetryAt <= :now)
                    or (j.status = :processing and j.lockedUntil < :now)
               )
            """)
    int claimForProcessing(
            @Param("requestId") Long requestId,
            @Param("workerId") String workerId,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now,
            @Param("requested") NotificationStatus requested,
            @Param("retryWaiting") NotificationStatus retryWaiting,
            @Param("processing") NotificationStatus processing
    );

    @Query("""
            select j
            from NotificationDeliveryJob j
            join fetch j.request
            where j.requestId = :requestId
              and j.status = :status
              and j.lockedBy = :workerId
            """)
    Optional<NotificationDeliveryJob> findClaimedWithRequest(
            @Param("requestId") Long requestId,
            @Param("status") NotificationStatus status,
            @Param("workerId") String workerId
    );

    Optional<NotificationDeliveryJob> findByRequestIdAndStatusAndLockedBy(Long requestId, NotificationStatus status, String lockedBy);
}
