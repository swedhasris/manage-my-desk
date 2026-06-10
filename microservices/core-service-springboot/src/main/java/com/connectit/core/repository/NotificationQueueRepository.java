package com.connectit.core.repository;

import com.connectit.core.model.NotificationQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long> {
    @Query("SELECT q FROM NotificationQueue q WHERE q.status IN ('pending','retry') AND (q.nextRetryAt IS NULL OR q.nextRetryAt <= :now) ORDER BY q.priority ASC, q.createdAt ASC")
    List<NotificationQueue> findPendingToProcess(LocalDateTime now);
    List<NotificationQueue> findByStatus(String status);
    @Query("SELECT q.status, COUNT(q) FROM NotificationQueue q GROUP BY q.status")
    List<Object[]> countByStatus();
}
