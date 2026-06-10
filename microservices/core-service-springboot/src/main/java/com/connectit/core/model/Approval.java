package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "approvals")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Approval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ticket_id",        nullable = false)    private Long ticketId;
    @Column(length = 20)                                    private String status = "Pending";
    @Column(name = "requested_by",     nullable = false, length = 128) private String requestedBy;
    @Column(name = "requested_by_name",length = 255)        private String requestedByName;
    @Column(name = "approved_by",      length = 128)        private String approvedBy;
    @Column(name = "approved_by_name", length = 255)        private String approvedByName;
    @Column(columnDefinition = "TEXT")                      private String comments;
    @Column(name = "created_at")                            private LocalDateTime createdAt;
    @Column(name = "updated_at")                            private LocalDateTime updatedAt;
    @Column(name = "approved_at")                           private LocalDateTime approvedAt;
    @PrePersist void prePersist()  { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate  void preUpdate()   { updatedAt = LocalDateTime.now(); }
}
