package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets", indexes = {
    @Index(name = "idx_tkt_number",   columnList = "ticket_number"),
    @Index(name = "idx_tkt_status",   columnList = "status"),
    @Index(name = "idx_tkt_priority", columnList = "priority"),
    @Index(name = "idx_tkt_assigned", columnList = "assigned_to"),
    @Index(name = "idx_tkt_created",  columnList = "created_by"),
    @Index(name = "idx_tkt_caller",   columnList = "caller")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", unique = true, nullable = false, length = 50)
    private String ticketNumber;

    @Column(nullable = false)
    private String caller;

    @Column(name = "caller_user_id", length = 128)
    private String callerUserId;

    @Column(name = "caller_email")
    private String callerEmail;

    @Column(name = "affected_user")
    private String affectedUser;

    @Column(name = "affected_user_id", length = 128)
    private String affectedUserId;

    @Column(length = 100)
    private String category;

    @Column(name = "incident_category", length = 100)
    private String incidentCategory;

    @Column(length = 100)
    private String subcategory;

    @Column(length = 100)
    private String service;

    @Column(name = "service_offering", length = 100)
    private String serviceOffering;

    @Column(name = "cmdb_item", length = 100)
    private String cmdbItem;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 30)
    private String channel = "Self-service";

    @Column(length = 30)
    private String status = "New";

    @Column(length = 20)
    private String impact = "3 - Low";

    @Column(length = 20)
    private String urgency = "3 - Low";

    @Column(length = 20)
    private String priority = "4 - Low";

    @Column(name = "assignment_group", length = 100)
    private String assignmentGroup;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "assigned_to_name")
    private String assignedToName;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "resolution_deadline")
    private LocalDateTime resolutionDeadline;

    @Column(name = "on_hold_start")
    private LocalDateTime onHoldStart;

    @Column(name = "on_hold_reason", length = 255)
    private String onHoldReason;

    @Column(name = "total_paused_time_ms")
    private Long totalPausedTimeMs = 0L;

    @Column(name = "response_sla_status", length = 20)
    private String responseSlaStatus = "In Progress";

    @Column(name = "resolution_sla_status", length = 20)
    private String resolutionSlaStatus = "In Progress";

    @Column(name = "response_sla_start_time")
    private LocalDateTime responseSlaStartTime;

    @Column(name = "resolution_sla_start_time")
    private LocalDateTime resolutionSlaStartTime;

    private Integer points = 0;

    @Column(name = "approval_status", length = 20)
    private String approvalStatus = "Not Required";

    @Column(name = "resolution_code", length = 100)
    private String resolutionCode;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolution_method", length = 100)
    private String resolutionMethod;

    @Column(name = "closure_reason", length = 100)
    private String closureReason;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "sla_delay_meta_json", columnDefinition = "LONGTEXT")
    private String slaDelayMetaJson;

    @Column(name = "sla_delay_logs_json", columnDefinition = "LONGTEXT")
    private String slaDelayLogsJson = "[]";

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
