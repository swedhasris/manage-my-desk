package com.connectit.core.repository;
import com.connectit.core.model.TimeCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@Repository
public interface TimeCardRepository extends JpaRepository<TimeCard, Long> {
    List<TimeCard> findByTimesheetId(Long timesheetId);
    List<TimeCard> findByUserId(String userId);
    List<TimeCard> findByUserIdAndEntryDateBetween(String userId, LocalDate start, LocalDate end);
    @Query("SELECT COALESCE(SUM(tc.hoursWorked), 0) FROM TimeCard tc WHERE tc.timesheetId = :tsId")
    BigDecimal sumHoursByTimesheetId(@Param("tsId") Long timesheetId);
    void deleteByTimesheetId(Long timesheetId);
}
