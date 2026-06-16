package com.connectit.core.repository;

import com.connectit.core.model.CallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CallLogRepository extends JpaRepository<CallLog, Long> {

    List<CallLog> findAllByOrderByCallDateTimeDesc();

    @Query("SELECT c FROM CallLog c WHERE " +
           "(COALESCE(:search, '') = '' OR " +
           " LOWER(c.callerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(c.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(c.agentName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(c.subject) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR c.status = :status) AND " +
           "(:callType IS NULL OR :callType = '' OR c.callType = :callType) AND " +
           "(:priority IS NULL OR :priority = '' OR c.priority = :priority) " +
           "ORDER BY c.callDateTime DESC")
    List<CallLog> searchCalls(@Param("search") String search,
                              @Param("status") String status,
                              @Param("callType") String callType,
                              @Param("priority") String priority);
}
