package com.connectit.core.controller;

import com.connectit.core.model.*;
import com.connectit.core.repository.*;
import com.connectit.core.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService   ticketService;
    private final TicketRepository ticketRepo;
    private final TicketActivityRepository activityRepo;
    private final UserRepository  userRepo;
    private final TicketCustomFieldRepository customFieldRepo;
    private final JdbcTemplate jdbcTemplate;

    // ── Health ────────────────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status","ok","service","ticklora-core","version","1.0.0"));
    }

    // ── Tickets ───────────────────────────────────────────────────────────────
    @GetMapping("/tickets/all")
    public ResponseEntity<?> all() {
        return ok(ticketRepo.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/tickets/open")
    public ResponseEntity<?> open() {
        return ok(ticketRepo.findAllOpen());
    }

    @GetMapping("/tickets/resolved")
    public ResponseEntity<?> resolved() {
        return ok(ticketRepo.findResolved());
    }

    @GetMapping("/tickets/unassigned")
    public ResponseEntity<?> unassigned() {
        return ok(ticketRepo.findUnassigned());
    }

    @GetMapping("/tickets/assigned/{userId}")
    public ResponseEntity<?> assigned(@PathVariable String userId) {
        return ok(ticketRepo.findByAssignedTo(userId));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return ticketRepo.findById(id)
            .map(t -> ResponseEntity.ok((Object) serialize(t)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tickets/create")
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
        try {
            String uid = SecurityContextHolder.getContext().getAuthentication().getName();
            String name = userRepo.findByUid(uid).map(User::getName).orElse(uid);
            boolean adminAccess = checkAdminAccess();
            Ticket t = ticketService.createTicket(body, uid, name, adminAccess);
            return ResponseEntity.status(201).body(serialize(t));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","Failed to create ticket: " + e.getMessage()));
        }
    }

    @PutMapping("/tickets/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        try {
            String uid = SecurityContextHolder.getContext().getAuthentication().getName();
            String name = userRepo.findByUid(uid).map(User::getName).orElse(uid);
            boolean adminAccess = checkAdminAccess();
            Ticket updated = ticketService.updateTicket(id, body, uid, name, adminAccess);
            return ResponseEntity.ok(serialize(updated));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("RCA")) return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            if (e.getMessage().contains("not found")) return ResponseEntity.notFound().build();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ticketRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message","Ticket deleted"));
    }

    @DeleteMapping("/tickets/all")
    public ResponseEntity<?> deleteAll() {
        ticketRepo.deleteAll();
        return ResponseEntity.ok(Map.of("message","All tickets deleted"));
    }

    @PostMapping("/tickets/trigger-escalation")
    public ResponseEntity<?> triggerEscalation() {
        return ResponseEntity.ok(Map.of("message","Escalation check triggered"));
    }

    // ── Ticket Custom Fields ──────────────────────────────────────────────────
    @GetMapping("/tickets/{id}/custom-fields")
    public ResponseEntity<?> getCustomFields(@PathVariable String id) {
        List<TicketCustomField> list = customFieldRepo.findByTicketId(id);
        Map<String, String> result = new HashMap<>();
        for (TicketCustomField f : list) {
            result.put(String.valueOf(f.getCategoryId()), f.getValueText());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tickets/{id}/custom-fields")
    @Transactional
    public ResponseEntity<?> saveCustomFields(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> customFields = (Map<String, String>) body.get("customFields");
        customFieldRepo.deleteByTicketId(id);
        if (customFields != null) {
            for (Map.Entry<String, String> entry : customFields.entrySet()) {
                String catIdStr = entry.getKey();
                String valText = entry.getValue();
                if (valText != null && !valText.isBlank()) {
                    int catId = Integer.parseInt(catIdStr);
                    String catName = "Field_" + catId;
                    try {
                        String name = jdbcTemplate.queryForObject(
                            "SELECT name FROM incident_categories WHERE id = ?",
                            String.class,
                            catId
                        );
                        if (name != null) catName = name;
                    } catch (Exception ignored) {}

                    TicketCustomField f = new TicketCustomField();
                    f.setTicketId(id);
                    f.setCategoryId(catId);
                    f.setCategoryName(catName);
                    f.setValueText(valText);
                    customFieldRepo.save(f);
                }
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Activities ────────────────────────────────────────────────────────────
    @GetMapping("/tickets/{id}/activities")
    public ResponseEntity<?> activities(@PathVariable Long id,
                                        @RequestParam(required=false) String visibility,
                                        @RequestParam(name="activity_type",required=false) String actTypes) {
        List<String> types = actTypes != null ? Arrays.asList(actTypes.split(",")) : null;
        List<TicketActivity> list = ticketService.getActivities(id, visibility, types);
        return ResponseEntity.ok(list.stream().map(this::serializeActivity).toList());
    }

    @PostMapping("/tickets/{id}/activities")
    public ResponseEntity<?> addActivity(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        if (body.get("message") == null || body.get("message").toString().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error","Message is required"));
        try {
            TicketActivity a = ticketService.addActivity(id, body);
            return ResponseEntity.status(201).body(serializeActivity(a));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tickets/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        body.put("activity_type", Boolean.TRUE.equals(body.get("is_internal")) ? "work_note" : "comment");
        body.put("visibility_type", Boolean.TRUE.equals(body.get("is_internal")) ? "internal" : "public");
        return addActivity(id, body);
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────
    @GetMapping("/leaderboard/daily")
    public ResponseEntity<?> leaderboard() {
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Object[]> rows = ticketRepo.findLeaderboard(today);
        List<Map<String,Object>> result = rows.stream().map(r -> Map.of(
            "id",            r[0],
            "name",          r[1] != null ? r[1] : r[0],
            "points",        r[2] != null ? r[2] : 0,
            "resolvedCount", r[3] != null ? r[3] : 0
        )).toList();
        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean checkAdminAccess() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return false;
            String uid = (String) auth.getPrincipal();
            return userRepo.findByUid(uid)
                .map(u -> List.of("admin","super_admin","ultra_super_admin").contains(u.getRole()))
                .orElse(false);
        } catch (Exception e) { return false; }
    }

    private Map<String,Object> serialize(Ticket t) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",                   String.valueOf(t.getId()));
        m.put("ticket_number",        t.getTicketNumber());
        m.put("caller",               t.getCaller());
        m.put("caller_email",         t.getCallerEmail());
        m.put("category",             t.getCategory());
        m.put("incident_category",    t.getIncidentCategory());
        m.put("title",                t.getTitle());
        m.put("description",          t.getDescription());
        m.put("status",               t.getStatus());
        m.put("priority",             t.getPriority());
        m.put("impact",               t.getImpact());
        m.put("urgency",              t.getUrgency());
        m.put("channel",              t.getChannel());
        m.put("assignment_group",     t.getAssignmentGroup());
        m.put("assigned_to",          t.getAssignedTo());
        m.put("assigned_to_name",     t.getAssignedToName());
        m.put("created_by",           t.getCreatedBy());
        m.put("created_by_name",      t.getCreatedByName());
        m.put("points",               t.getPoints());
        m.put("response_deadline",    t.getResponseDeadline());
        m.put("resolution_deadline",  t.getResolutionDeadline());
        m.put("first_response_at",    t.getFirstResponseAt());
        m.put("resolved_at",          t.getResolvedAt());
        m.put("response_sla_status",  t.getResponseSlaStatus());
        m.put("resolution_sla_status",t.getResolutionSlaStatus());
        m.put("created_at",           t.getCreatedAt());
        m.put("updated_at",           t.getUpdatedAt());
        m.put("slaDelayMeta",         parseMeta(t.getSlaDelayMetaJson()));
        m.put("slaDelayLogs",         parseMeta(t.getSlaDelayLogsJson()));
        return m;
    }

    private Object parseMeta(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return json; // Return raw JSON string — Jackson will serialize it
    }

    private Map<String,Object> serializeActivity(TicketActivity a) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",              String.valueOf(a.getId()));
        m.put("ticket_id",       String.valueOf(a.getTicketId()));
        m.put("activity_type",   a.getActivityType());
        m.put("visibility_type", a.getVisibilityType());
        m.put("created_by",      a.getCreatedBy());
        m.put("created_by_name", a.getCreatedByName());
        m.put("message",         a.getMessage());
        m.put("metadata_json",   a.getMetadataJson());
        m.put("created_at",      a.getCreatedAt());
        return m;
    }

    private ResponseEntity<?> ok(List<Ticket> tickets) {
        return ResponseEntity.ok(tickets.stream().map(this::serialize).toList());
    }
}
