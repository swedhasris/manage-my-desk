package com.connectit.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.connectit.core.util.DbUtil;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final JdbcTemplate jdbcTemplate;

    private static final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ── GET Notifications List ────────────────────────────────────────────────
    @GetMapping(value = {"", "/list"})
    public ResponseEntity<?> list(
            @RequestParam(name="user_id") String userId,
            @RequestParam(defaultValue="50") int limit) {
        
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId, limit);
        return ResponseEntity.ok(stringifyIds(rows));
    }

    // ── GET Unread Count ──────────────────────────────────────────────────────
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@RequestParam(name="user_id") String userId) {
        String sql = "SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0";
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, userId);
        return ResponseEntity.ok(Map.of("count", row.get("count")));
    }

    // ── POST Mark Read ────────────────────────────────────────────────────────
    @PostMapping("/mark-read")
    @Transactional
    public ResponseEntity<?> markRead(@RequestBody Map<String, String> body) {
        String userId = body.get("user_id");
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "user_id required"));
        }
        jdbcTemplate.update("UPDATE notifications SET is_read = 1 WHERE user_id = ?", userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── SSE Stream Endpoint ───────────────────────────────────────────────────
    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam(name="user_id") String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));
        
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (Exception ignored) {}
        
        return emitter;
    }

    // ── POST Dispatch Notification ────────────────────────────────────────────
    @PostMapping("/dispatch")
    @Transactional
    public ResponseEntity<?> dispatch(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> ticket = (Map<String, Object>) body.get("ticket");
            if (ticket == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing ticket data"));

            String actorId = (String) body.get("actorId");
            String actorName = (String) body.get("actorName");
            String type = (String) body.get("type");
            String oldStatus = (String) body.get("oldStatus");
            String newStatus = (String) body.get("newStatus");
            String oldAssignee = (String) body.get("oldAssignee");
            String newAssignee = (String) body.get("newAssignee");

            String message = "";
            if ("create".equals(type)) {
                String creatorName = ticket.get("created_by_name") != null ? (String) ticket.get("created_by_name") : "System";
                String assigneeName = ticket.get("assigned_to_name") != null ? (String) ticket.get("assigned_to_name") : "Unassigned";
                message = creatorName + " created a ticket and assigned it to " + assigneeName;
            } else {
                message = actorName + " updated ticket #" + ticket.get("ticket_number");

                if (newStatus != null && oldStatus != null && !newStatus.equals(oldStatus)) {
                    if ("Resolved".equals(newStatus) || "Closed".equals(newStatus)) {
                        message = actorName + " resolved ticket #" + ticket.get("ticket_number");
                    } else {
                        message = actorName + " changed ticket #" + ticket.get("ticket_number") + " status from " + oldStatus + " to " + newStatus;
                    }
                } else if (newAssignee != null && !newAssignee.equals(oldAssignee)) {
                    String creatorName = ticket.get("created_by_name") != null ? (String) ticket.get("created_by_name") : "System";
                    String assigneeName = ticket.get("assigned_to_name") != null ? (String) ticket.get("assigned_to_name") : "Unassigned";
                    message = creatorName + " assigned ticket #" + ticket.get("ticket_number") + " to " + assigneeName;
                }
            }

            dispatchNotifications(ticket, actorId, actorName, message);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception err) {
            return ResponseEntity.status(500).body(Map.of("error", err.getMessage()));
        }
    }

    private void dispatchNotifications(Map<String, Object> ticket, String actorId, String actorName, String message) {
        try {
            List<Map<String, Object>> allUsers = jdbcTemplate.queryForList("SELECT uid, name, role FROM users");

            String ticketCreatedBy = (String) ticket.get("created_by");
            String ticketAssignedTo = (String) ticket.get("assigned_to");
            String ticketNumber = (String) ticket.get("ticket_number");
            String ticketId = String.valueOf(ticket.get("id"));

            Map<String, Object> creatorUser = allUsers.stream().filter(u -> ticketCreatedBy != null && ticketCreatedBy.equals(u.get("uid"))).findFirst().orElse(null);
            Map<String, Object> assigneeUser = allUsers.stream().filter(u -> ticketAssignedTo != null && ticketAssignedTo.equals(u.get("uid"))).findFirst().orElse(null);

            String creatorRole = creatorUser != null && creatorUser.get("role") != null ? (String) creatorUser.get("role") : "user";
            String assigneeRole = assigneeUser != null && assigneeUser.get("role") != null ? (String) assigneeUser.get("role") : "user";

            boolean isCreatorManaged = "user".equals(creatorRole) || "agent".equals(creatorRole);
            boolean isAssigneeManaged = "user".equals(assigneeRole) || "agent".equals(assigneeRole);

            for (Map<String, Object> user : allUsers) {
                String role = (String) user.get("role");
                String uid = (String) user.get("uid");
                if (uid == null) continue;

                boolean eligible = false;
                if ("super_admin".equals(role) || "ultra_super_admin".equals(role) || "admin".equals(role)) {
                    eligible = true;
                } else if ("sub_admin".equals(role)) {
                    eligible = isCreatorManaged || isAssigneeManaged;
                } else if ("agent".equals(role)) {
                    eligible = uid.equals(ticketAssignedTo);
                } else if ("user".equals(role)) {
                    eligible = uid.equals(ticketCreatedBy) || uid.equals(ticketAssignedTo);
                }

                if (eligible) {
                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    String insertSql = "INSERT INTO notifications (user_id, message, ticket_id, ticket_number, actor_id, actor_name, is_read) VALUES (?, ?, ?, ?, ?, ?, 0)";
                    jdbcTemplate.update(connection -> {
                        PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, uid);
                        ps.setString(2, message);
                        ps.setString(3, ticketId);
                        ps.setString(4, ticketNumber);
                        ps.setString(5, actorId);
                        ps.setString(6, actorName);
                        return ps;
                    }, keyHolder);

                    long newId = DbUtil.getGeneratedId(keyHolder);

                    Map<String, Object> newNotif = new HashMap<>();
                    newNotif.put("id", String.valueOf(newId));
                    newNotif.put("user_id", uid);
                    newNotif.put("message", message);
                    newNotif.put("ticket_id", ticketId);
                    newNotif.put("ticket_number", ticketNumber);
                    newNotif.put("actor_id", actorId);
                    newNotif.put("actor_name", actorName);
                    newNotif.put("is_read", 0);
                    newNotif.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                    sendNotification(uid, newNotif);
                }
            }
        } catch (Exception e) {
            System.err.println("[Notifications Dispatcher] Error: " + e.getMessage());
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    public static void sendNotification(String userId, Map<String, Object> notif) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().data(notif));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            list.removeAll(deadEmitters);
        }
    }

    private List<Map<String, Object>> stringifyIds(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(stringifyId(new HashMap<>(row)));
        }
        return result;
    }

    private Map<String, Object> stringifyId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Map<String, Object> lowerRow = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            lowerRow.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        Object idVal = lowerRow.get("id");
        if (idVal != null) {
            lowerRow.put("id", String.valueOf(idVal));
        }
        return lowerRow;
    }
}
