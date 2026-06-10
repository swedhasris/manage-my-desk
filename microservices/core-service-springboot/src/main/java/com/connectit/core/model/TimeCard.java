package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "time_cards", indexes = {
    @Index(name = "idx_tc_ts",      columnList = "timesheet_id"),
    @Index(name = "idx_tc_user_dt", columnList = "user_id,entry_date")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TimeCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)     private Long id;
    @Column(name = "timesheet_id", nullable = false)            private Long timesheetId;
    @Column(name = "user_id", nullable = false, length = 128)   private String userId;
    @Column(name = "entry_date", nullable = false)              private LocalDate entryDate;
    @Column(length = 255)                                       private String task;
    @Column(name = "hours_worked", precision = 10, scale = 2)   private BigDecimal hoursWorked = BigDecimal.ZERO;
    @Column(columnDefinition = "TEXT")                          private String description;
    @Column(name = "short_description", length = 255)           private String shortDescription;
    @Column(name = "start_time", length = 20)                   private String startTime;
    @Column(name = "end_time",   length = 20)                   private String endTime;
    @Column(precision = 10, scale = 2)                          private BigDecimal deduct = BigDecimal.ZERO;
    @Column(name = "work_type",  length = 50)                   private String workType;
    @Column(length = 50)                                        private String billable;
    @Column(columnDefinition = "TEXT")                          private String notes;
    @Column(length = 20)                                        private String status = "Draft";
    @Column(name = "elapsed_seconds")                           private Integer elapsedSeconds = 0;
    @Column(name = "created_at")                                private LocalDateTime createdAt;
    @Column(name = "updated_at")                                private LocalDateTime updatedAt;
    @PrePersist  void prePersist()  { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate   void preUpdate()   { updatedAt = LocalDateTime.now(); }
}
