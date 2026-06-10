package com.connectit.core.controller;

import com.connectit.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final TicketRepository ticketRepo;
    private final UserRepository   userRepo;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> stats() {
        long open         = ticketRepo.countByStatusNotIn(List.of("Resolved","Closed","Canceled"));
        long unassigned   = ticketRepo.countByAssignedToIsNullOrAssignedTo("");
        long critical     = ticketRepo.countByPriorityAndStatusNotIn("1 - Critical", List.of("Resolved","Closed","Canceled"));
        long slaBreached  = ticketRepo.countByResolutionSlaStatusAndStatusNotIn("Breached", List.of("Resolved","Closed","Canceled"));
        long resolvedToday= ticketRepo.countResolvedToday(LocalDateTime.now().toLocalDate().atStartOfDay());
        long totalAgents  = userRepo.findByRoleInAndIsActiveTrue(
            List.of("agent","admin","super_admin","ultra_super_admin")).size();

        return ResponseEntity.ok(Map.of(
            "open",            open,
            "unassigned",      unassigned,
            "critical",        critical,
            "sla_breached",    slaBreached,
            "resolved_today",  resolvedToday,
            "total_agents",    totalAgents
        ));
    }

    @GetMapping("/leaderboard/daily")
    public ResponseEntity<?> leaderboard() {
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Object[]> rows = ticketRepo.findLeaderboard(today);
        List<Map<String,Object>> result = rows.stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",            r[0]);
            m.put("name",          r[1] != null ? r[1] : r[0]);
            m.put("points",        r[2] != null ? r[2] : 0);
            m.put("resolvedCount", r[3] != null ? r[3] : 0);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/db-test")
    public ResponseEntity<?> dbTest() {
        try {
            long count = ticketRepo.count();
            return ResponseEntity.ok(Map.of("status","connected","ticket_count", count));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }
}
