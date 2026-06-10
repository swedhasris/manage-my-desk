package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "comments", indexes = {
    @Index(name = "idx_cmt_ticket",  columnList = "ticket_id"),
    @Index(name = "idx_cmt_user_id", columnList = "user_id")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ticket_id", nullable = false)           private Long ticketId;
    @Column(name = "user_id",   length = 128)               private String userId;
    @Column(name = "user_name")                             private String userName;
    @Column(name = "user_role", length = 50)                private String userRole;
    @Column(nullable = false, columnDefinition = "TEXT")    private String message;
    @Column(name = "is_internal")                           private Boolean isInternal = false;
    @Column(name = "created_at")                            private LocalDateTime createdAt;
    @Column(name = "updated_at")                            private LocalDateTime updatedAt;
    @PrePersist void prePersist()  { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate  void preUpdate()   { updatedAt = LocalDateTime.now(); }
}
