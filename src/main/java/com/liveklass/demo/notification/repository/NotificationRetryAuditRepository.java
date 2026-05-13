package com.liveklass.demo.notification.repository;

import com.liveklass.demo.notification.domain.NotificationRetryAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRetryAuditRepository extends JpaRepository<NotificationRetryAudit, Long> {

    List<NotificationRetryAudit> findByRequestIdOrderByRetriedAtAsc(Long requestId);
}
