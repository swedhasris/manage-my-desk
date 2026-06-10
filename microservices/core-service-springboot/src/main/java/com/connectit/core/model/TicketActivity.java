package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_activities", indexes = {
    @Index(name = "idx_ta_ticket",     columnList = "ticket_id"),
    @Index(name = "idx_ta_created_at", columnList = "created_at"),
    @Index(name = "idx_ta_visibility", columnList = "visibility_type")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketActivity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "visibility_type", nullable = false, length = 50)
    private String visibilityType;

    @Column(length = 50)
    private String channel = "portal";

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "thread_id", length = 255)
    private String threadId;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
