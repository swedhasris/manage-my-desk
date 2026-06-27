package com.connectit.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Safe, flat DTO returned by ticket endpoints.
 * Never exposes JPA entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private String id;
    private String ticketNumber;
    private String caller;
    private String callerEmail;
    private String affectedUser;
    private String category;
    private String incidentCategory;
    private String subcategory;
    private String service;
    private String serviceOffering;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String impact;
    private String urgency;
    private String channel;
    private String assignmentGroup;
    private String assignedTo;
    private String assignedToName;
    private String createdBy;
    private String createdByName;
    private Integer points;
    // SLA fields
    private String responseSlaStatus;
    private String resolutionSlaStatus;
    private Long totalPausedTimeMs;
    private LocalDateTime responseDeadline;
    private LocalDateTime resolutionDeadline;
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    // Approval
    private String approvalStatus;
    // Resolution
    private String resolutionCode;
    private String resolutionNotes;
    private String resolutionMethod;
    private String closureReason;
    private String resolvedBy;
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
