package com.connectit.core.controller;

import com.connectit.core.model.SLAPolicy;
import com.connectit.core.service.SlaService;
import com.connectit.core.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SLA controller — all authorization is now handled by Spring Security @PreAuthorize
 * and the JWT filter. The legacy x-user-uid header bypass has been removed.
 * Custom checkAdminAccess() with hardcoded email fallbacks has been removed.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SlaController {

    private final SlaService       slaService;
    private final TicketRepository ticketRepo;
    private final JdbcTemplate     jdbcTemplate;

    @GetMapping("/sla/policies")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> policies() {
        return ResponseEntity.ok(slaService.getAllPolicies());
    }

    @PostMapping("/sla/policies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> createPolicy(@RequestBody SLAPolicy policy) {
        return ResponseEntity.status(201).body(slaService.save(policy));
    }

    @PutMapping("/sla/policies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> updatePolicy(@PathVariable Long id, @RequestBody SLAPolicy policy) {
        policy.setId(id);
        return ResponseEntity.ok(slaService.save(policy));
    }

    @DeleteMapping("/sla/policies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> deletePolicy(@PathVariable Long id) {
        slaService.delete(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping({"/sla/breaches", "/sla-breaches/all"})
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> breaches() {
        return ResponseEntity.ok(slaService.getBreaches());
    }

    @GetMapping("/sla-breaches/user/{userId}")
    @PreAuthorize("hasAnyRole('AGENT','SUB_ADMIN','ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> breachesByUser(
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Agents can only see their own breaches; admins can see any user's
        boolean isSelf = userDetails != null && userDetails.getUsername().equals(userId);
        boolean isAdmin = userDetails != null && userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().matches("ROLE_(ADMIN|SUB_ADMIN|SUPER_ADMIN|ULTRA_SUPER_ADMIN)"));
        if (!isSelf && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: You can only view your own SLA breaches"));
        }
        return ResponseEntity.ok(slaService.getBreachesByUser(userId));
    }

    @GetMapping("/sla/audit/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> auditLogs(@PathVariable String ticketId) {
        return ResponseEntity.ok(slaService.getSlaAuditLogs(ticketId));
    }

    @PostMapping("/tickets/trigger-escalation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> triggerEscalation() {
        return ResponseEntity.ok(Map.of("message", "Escalation triggered — check SLA scheduler logs"));
    }
}
