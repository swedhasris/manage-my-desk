package com.connectit.core.repository;

import com.connectit.core.model.TicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {
    List<TicketActivity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    List<TicketActivity> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    List<TicketActivity> findByTicketIdAndVisibilityTypeOrderByCreatedAtAsc(Long ticketId, String visibility);
    List<TicketActivity> findByTicketIdAndVisibilityTypeOrderByCreatedAtDesc(Long ticketId, String visibility);
    List<TicketActivity> findByTicketIdAndActivityTypeInOrderByCreatedAtAsc(Long ticketId, List<String> types);
}
