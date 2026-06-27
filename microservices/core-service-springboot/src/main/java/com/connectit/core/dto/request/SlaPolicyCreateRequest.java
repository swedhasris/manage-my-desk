package com.connectit.core.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaPolicyCreateRequest {

    @NotBlank(message = "SLA policy name is required")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Priority is required")
    private String priority;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Response time hours is required")
    @Min(value = 1, message = "Response time must be at least 1 hour")
    private Integer responseTimeHours;

    @NotNull(message = "Resolution time hours is required")
    @Min(value = 1, message = "Resolution time must be at least 1 hour")
    private Integer resolutionTimeHours;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
}
