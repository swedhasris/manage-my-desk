package com.connectit.core.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardLayoutController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Get Layout for User ──────────────────────────────────────────────────
    @GetMapping("/layout")
    public ResponseEntity<?> getLayout(
            @RequestParam(required = false) String userUid,
            @RequestParam(required = false) String role) {
        log.info("[DashboardLayout] Fetching layout for userUid: {}, role: {}", userUid, role);
        try {
            if (userUid != null && !userUid.isBlank()) {
                List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                    "SELECT layout_json FROM settings_dashboard_layouts WHERE user_uid = ?", userUid
                );
                if (!userRows.isEmpty()) {
                    String layoutJson = (String) userRows.get(0).get("layout_json");
                    return ResponseEntity.ok(objectMapper.readValue(layoutJson, List.class));
                }
            }

            // Fallback to role-specific template
            String targetRole = (role != null) ? role.toLowerCase() : "user";
            List<Map<String, Object>> templateRows = jdbcTemplate.queryForList(
                "SELECT layout_json FROM settings_dashboard_templates WHERE LOWER(role) = ? ORDER BY is_locked DESC, created_at DESC LIMIT 1",
                targetRole
            );
            if (!templateRows.isEmpty()) {
                String layoutJson = (String) templateRows.get(0).get("layout_json");
                return ResponseEntity.ok(objectMapper.readValue(layoutJson, List.class));
            }

            // Global default widgets fallback
            List<String> defaultWidgets = List.of(
                "Open Tickets", "Closed Tickets", "Pending Tickets", 
                "SLA Compliance", "Escalated Tickets", "Ticket Trend Chart"
            );
            return ResponseEntity.ok(defaultWidgets);
        } catch (Exception e) {
            log.error("[DashboardLayout] Error fetching dashboard layout: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Save User Layout ─────────────────────────────────────────────────────
    @PostMapping("/layout")
    @Transactional
    public ResponseEntity<?> saveLayout(@RequestBody Map<String, Object> body) {
        log.info("[DashboardLayout] Saving layout: {}", body);
        try {
            String userUid = (String) body.get("userUid");
            List<?> layout = (List<?>) body.get("layout");
            if (userUid == null || userUid.isBlank() || layout == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing userUid or layout"));
            }

            String layoutJson = objectMapper.writeValueAsString(layout);
            String id = "lay_" + System.currentTimeMillis();

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM settings_dashboard_layouts WHERE user_uid = ?", userUid
            );

            if (!existing.isEmpty()) {
                jdbcTemplate.update(
                    "UPDATE settings_dashboard_layouts SET layout_json = ?, updated_at = CURRENT_TIMESTAMP WHERE user_uid = ?",
                    layoutJson, userUid
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO settings_dashboard_layouts (id, user_uid, layout_json) VALUES (?, ?, ?)",
                    id, userUid, layoutJson
                );
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[DashboardLayout] Error saving dashboard layout: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Reset User Layout ────────────────────────────────────────────────────
    @DeleteMapping("/layout")
    @Transactional
    public ResponseEntity<?> resetLayout(@RequestParam String userUid) {
        log.info("[DashboardLayout] Resetting layout for userUid: {}", userUid);
        try {
            jdbcTemplate.update("DELETE FROM settings_dashboard_layouts WHERE user_uid = ?", userUid);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[DashboardLayout] Error resetting dashboard layout: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Get Templates (Admin) ────────────────────────────────────────────────
    @GetMapping("/templates")
    public ResponseEntity<?> getTemplates() {
        log.info("[DashboardLayout] Listing all templates");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM settings_dashboard_templates ORDER BY created_at DESC"
            );
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>(r);
                m.put("layout", objectMapper.readValue((String) r.get("layout_json"), List.class));
                m.put("isLocked", ((Number) r.get("is_locked")).intValue() == 1);
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[DashboardLayout] Error listing templates: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Create or Update Template (Admin) ────────────────────────────────────
    @PostMapping("/templates")
    @Transactional
    public ResponseEntity<?> saveTemplate(@RequestBody Map<String, Object> body) {
        log.info("[DashboardLayout] Saving template: {}", body);
        try {
            String id = (String) body.get("id");
            String name = (String) body.get("name");
            String role = (String) body.get("role");
            List<?> layout = (List<?>) body.get("layout");
            boolean isLocked = (Boolean) body.getOrDefault("isLocked", false);

            if (name == null || role == null || layout == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing name, role or layout"));
            }

            String layoutJson = objectMapper.writeValueAsString(layout);

            if (id != null && !id.isBlank()) {
                jdbcTemplate.update(
                    "UPDATE settings_dashboard_templates SET name = ?, role = ?, layout_json = ?, is_locked = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    name, role.toLowerCase(), layoutJson, isLocked ? 1 : 0, id
                );
            } else {
                id = "tpl_" + System.currentTimeMillis();
                jdbcTemplate.update(
                    "INSERT INTO settings_dashboard_templates (id, name, role, layout_json, is_locked) VALUES (?, ?, ?, ?, ?)",
                    id, name, role.toLowerCase(), layoutJson, isLocked ? 1 : 0
                );
            }

            return ResponseEntity.ok(Map.of("success", true, "id", id));
        } catch (Exception e) {
            log.error("[DashboardLayout] Error saving template: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
