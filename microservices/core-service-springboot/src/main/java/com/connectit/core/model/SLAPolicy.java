package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "sla_policies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"priority","category"})
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SLAPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false)              private String name;
    @Column(nullable = false, length = 50) private String priority;
    @Column(length = 100)                  private String category;
    @Column(name = "response_time_hours",   nullable = false) private Integer responseTimeHours;
    @Column(name = "resolution_time_hours", nullable = false) private Integer resolutionTimeHours;
    @Column(name = "is_active")            private Boolean isActive = true;
    @Column(columnDefinition = "TEXT")     private String description;
    @Column(name = "created_at")           private LocalDateTime createdAt;
    @Column(name = "updated_at")           private LocalDateTime updatedAt;
    @PrePersist  void prePersist()  { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate   void preUpdate()   { updatedAt = LocalDateTime.now(); }
}
