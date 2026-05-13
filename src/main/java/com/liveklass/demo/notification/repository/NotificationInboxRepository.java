package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationInbox;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationInboxRepository extends JpaRepository<NotificationInbox, Long> {

    @Query("""
            select i
            from NotificationInbox i
            join fetch i.request
            where i.requestId = :requestId
            """)
    Optional<NotificationInbox> findByRequestIdWithRequest(@Param("requestId") Long requestId);

    @Query("""
            select i
            from NotificationInbox i
            join fetch i.request
            where i.recipientId = :recipientId
            order by i.createdAt desc
            """)
    List<NotificationInbox> findByRecipientIdWithRequestOrderByCreatedAtDesc(@Param("recipientId") String recipientId);

    @Query("""
            select i
            from NotificationInbox i
            join fetch i.request
            where i.recipientId = :recipientId
              and i.readAt is null
            order by i.createdAt desc
            """)
    List<NotificationInbox> findUnreadByRecipientIdWithRequestOrderByCreatedAtDesc(@Param("recipientId") String recipientId);

    @Query("""
            select i
            from NotificationInbox i
            join fetch i.request
            where i.recipientId = :recipientId
              and i.readAt is not null
            order by i.createdAt desc
            """)
    List<NotificationInbox> findReadByRecipientIdWithRequestOrderByCreatedAtDesc(@Param("recipientId") String recipientId);

    @Query("""
            select new com.liveklass.demo.notification.repository.NotificationInboxWithJobView(i, r, j)
            from NotificationInbox i
            join i.request r
            join NotificationDeliveryJob j on j.requestId = i.requestId
            where i.recipientId = :recipientId
            order by i.createdAt desc
            """)
    List<NotificationInboxWithJobView> findDetailsByRecipientIdOrderByCreatedAtDesc(@Param("recipientId") String recipientId);

    @Query("""
            select new com.liveklass.demo.notification.repository.NotificationInboxWithJobView(i, r, j)
            from NotificationInbox i
            join i.request r
            join NotificationDeliveryJob j on j.requestId = i.requestId
            where i.recipientId = :recipientId
              and i.readAt is null
            order by i.createdAt desc
            """)
    List<NotificationInboxWithJobView> findUnreadDetailsByRecipientIdOrderByCreatedAtDesc(@Param("recipientId") String recipientId);

    @Query("""
            select new com.liveklass.demo.notification.repository.NotificationInboxWithJobView(i, r, j)
            from NotificationInbox i
            join i.request r
            join NotificationDeliveryJob j on j.requestId = i.requestId
            where i.recipientId = :recipientId
              and i.readAt is not null
            order by i.createdAt desc
            """)
    List<NotificationInboxWithJobView> findReadDetailsByRecipientIdOrderByCreatedAtDesc(@Param("recipientId") String recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationInbox i
               set i.readAt = :now,
                   i.updatedAt = :now
             where i.requestId = :requestId
               and i.recipientId = :recipientId
               and i.readAt is null
            """)
    int markReadIfUnread(@Param("requestId") Long requestId, @Param("recipientId") String recipientId, @Param("now") Instant now);
}
