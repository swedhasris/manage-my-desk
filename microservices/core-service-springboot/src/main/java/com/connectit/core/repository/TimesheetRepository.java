package com.connectit.core.repository;
import com.connectit.core.model.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
    List<Timesheet> findByUserIdOrderByUpdatedAtDesc(String userId);
    List<Timesheet> findAllByOrderByUpdatedAtDesc();
    Optional<Timesheet> findByUserIdAndWeekStart(String userId, LocalDate weekStart);
    List<Timesheet> findByStatus(String status);
}
