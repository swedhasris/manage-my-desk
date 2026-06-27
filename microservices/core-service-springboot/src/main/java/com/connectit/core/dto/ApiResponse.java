package com.connectit.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API envelope for all controller responses.
 *
 * Every endpoint must return {@code ResponseEntity<ApiResponse<T>>}.
 * Never return raw entities, Maps, or Strings from controllers.
 *
 * Usage:
 * <pre>
 *   return ResponseEntity.ok(ApiResponse.success("Ticket created", response));
 *   return ResponseEntity.status(400).body(ApiResponse.error("Title is required"));
 * </pre>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // ── Factory helpers ───────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
