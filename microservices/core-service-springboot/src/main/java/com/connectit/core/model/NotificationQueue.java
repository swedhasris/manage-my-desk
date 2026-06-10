package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications_queue", indexes = {
    @Index(name = "idx_nq_status", columnList = "status")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationQueue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)  private String eventType;
    @Column(name = "ticket_id")                      private Long ticketId;
    @Column(name = "ticket_number", length = 64)     private String ticketNumber;
    @Column(nullable = false)                        private String recipient;
    private String subject;
    @Column(name = "body_html", columnDefinition = "LONGTEXT") private String bodyHtml;
    @Column(length = 30)                             private String status = "pending";
    private Integer priority = 3;
    @Column(name = "retry_count")                    private Integer retryCount = 0;
    @Column(name = "max_retries")                    private Integer maxRetries = 5;
    @Column(name = "next_retry_at")                  private LocalDateTime nextRetryAt;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "config_id")                      private Long configId;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;
    @Column(name = "created_at")                     private LocalDateTime createdAt;
    @Column(name = "processed_at")                   private LocalDateTime processedAt;

    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
}
