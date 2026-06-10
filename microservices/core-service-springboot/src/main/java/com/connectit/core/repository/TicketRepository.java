package com.connectit.core.repository;

import com.connectit.core.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('Resolved','Closed','Canceled') ORDER BY t.createdAt DESC")
    List<Ticket> findAllOpen();

    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('Resolved','Closed','Canceled') ORDER BY t.createdAt DESC")
    List<Ticket> findAllOpenTickets();

    @Query("SELECT t FROM Ticket t WHERE t.assignedTo = :uid ORDER BY t.createdAt DESC")
    List<Ticket> findByAssignedTo(@Param("uid") String uid);

    @Query("SELECT t FROM Ticket t WHERE (t.assignedTo IS NULL OR t.assignedTo = '') ORDER BY t.createdAt DESC")
    List<Ticket> findUnassigned();

    @Query("SELECT t FROM Ticket t WHERE (t.assignedTo IS NULL OR t.assignedTo = '') ORDER BY t.createdAt DESC")
    List<Ticket> findUnassignedTickets();

    List<Ticket> findByStatus(String status);

    @Query("SELECT t FROM Ticket t WHERE t.status IN ('Resolved','Closed') ORDER BY t.resolvedAt DESC")
    List<Ticket> findResolved();

    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('Resolved','Closed','Canceled')")
    List<Ticket> findAllNonClosed();

    // Leaderboard
    @Query("SELECT t.assignedTo, t.assignedToName, SUM(t.points) as totalPoints, COUNT(t) as resolvedCount " +
           "FROM Ticket t WHERE t.status IN ('Resolved','Closed') AND t.resolvedAt >= :since AND t.assignedTo IS NOT NULL " +
           "GROUP BY t.assignedTo, t.assignedToName ORDER BY SUM(t.points) DESC")
    List<Object[]> findLeaderboard(@Param("since") LocalDateTime since);

    // Dashboard stats
    long countByStatusNotIn(List<String> statuses);
    long countByAssignedToIsNullOrAssignedTo(String empty);
    long countByStatusIn(List<String> statuses);
    long countByPriorityAndStatusNotIn(String priority, List<String> statuses);
    long countByResolutionSlaStatusAndStatusNotIn(String slaStatus, List<String> statuses);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN ('Resolved','Closed') AND t.resolvedAt >= :today")
    long countResolvedToday(@Param("today") LocalDateTime today);
}
