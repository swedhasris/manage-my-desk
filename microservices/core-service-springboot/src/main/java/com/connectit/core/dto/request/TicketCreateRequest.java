package com.connectit.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title cannot exceed 500 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    private String subcategory;

    @NotBlank(message = "Priority is required")
    private String priority;

    private String urgency;
    private String impact;
    private String caller;
    private String affectedUser;
    private String assignmentGroup;
    private String assignedTo;
    private Map<String, Object> customFields;
}
