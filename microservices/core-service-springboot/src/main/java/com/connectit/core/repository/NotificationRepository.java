package com.connectit.core.repository;

import com.connectit.core.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByUserIdAndIsReadFalse(String userId);

    @Query(value = "SELECT * FROM notifications WHERE user_id = :userId ORDER BY created_at DESC LIMIT 50", nativeQuery = true)
    List<Notification> findByUserIdOrderByCreatedAtDescWithLimit(@Param("userId") String userId);
}
