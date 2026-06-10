package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "timesheets", indexes = {
    @Index(name = "idx_ts_user_week", columnList = "user_id,week_start"),
    @Index(name = "idx_ts_status",    columnList = "status")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Timesheet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, length = 128) private String userId;
    @Column(name = "week_start", nullable = false)            private LocalDate weekStart;
    @Column(name = "week_end",   nullable = false)            private LocalDate weekEnd;
    @Column(length = 20)                                      private String status = "Draft";
    @Column(name = "total_hours", precision = 10, scale = 2)  private BigDecimal totalHours = BigDecimal.ZERO;
    @Column(name = "screenshot_url", columnDefinition = "TEXT") private String screenshotUrl;
    @Column(name = "approved_by", length = 128)               private String approvedBy;
    @Column(name = "approved_at")                             private LocalDateTime approvedAt;
    @Column(name = "rejection_reason", columnDefinition = "TEXT") private String rejectionReason;
    @Column(name = "created_at")                              private LocalDateTime createdAt;
    @Column(name = "updated_at")                              private LocalDateTime updatedAt;
    @Column(name = "submitted_at")                            private LocalDateTime submittedAt;
    @PrePersist  void prePersist()  { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate   void preUpdate()   { updatedAt = LocalDateTime.now(); }
}
