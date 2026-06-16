package com.connectit.core.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@Slf4j
public class PlanningController {

    private final JdbcTemplate jdbcTemplate;

    // ── Get Targets ──────────────────────────────────────────────────────────
    @GetMapping("/targets")
    public ResponseEntity<?> getTargets(
            @RequestParam(required = false) String targetPeriod,
            @RequestParam(required = false) String assigneeUid) {
        log.info("[Planning] Fetching targets. Period: {}, Assignee: {}", targetPeriod, assigneeUid);
        try {
            String sql = "SELECT * FROM planning_targets WHERE 1=1";
            List<Object> params = new ArrayList<>();
            if (targetPeriod != null && !targetPeriod.isBlank()) {
                sql += " AND target_period = ?";
                params.add(targetPeriod);
            }
            if (assigneeUid != null && !assigneeUid.isBlank()) {
                sql += " AND assignee_uid = ?";
                params.add(assigneeUid);
            }
            sql += " ORDER BY target_period DESC, metric_name ASC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("[Planning] Error fetching targets: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Save/Update Target ───────────────────────────────────────────────────
    @PostMapping("/targets")
    @Transactional
    public ResponseEntity<?> saveTarget(@RequestBody Map<String, Object> body) {
        log.info("[Planning] Saving target: {}", body);
        try {
            String id = (String) body.get("id");
            String targetType = (String) body.get("targetType");
            String targetPeriod = (String) body.get("targetPeriod");
            String metricName = (String) body.get("metricName");
            Number targetValue = (Number) body.get("targetValue");
            Number actualValue = (Number) body.getOrDefault("actualValue", 0.0);
            String teamId = (String) body.get("teamId");
            String assigneeUid = (String) body.get("assigneeUid");

            if (targetType == null || targetPeriod == null || metricName == null || targetValue == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            if (id != null && !id.isBlank()) {
                jdbcTemplate.update(
                    "UPDATE planning_targets SET target_type = ?, target_period = ?, metric_name = ?, target_value = ?, actual_value = ?, team_id = ?, assignee_uid = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    targetType, targetPeriod, metricName, targetValue.doubleValue(), actualValue.doubleValue(), teamId, assigneeUid, id
                );
            } else {
                id = "tgt_" + System.currentTimeMillis();
                jdbcTemplate.update(
                    "INSERT INTO planning_targets (id, target_type, target_period, metric_name, target_value, actual_value, team_id, assignee_uid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, targetType, targetPeriod, metricName, targetValue.doubleValue(), actualValue.doubleValue(), teamId, assigneeUid
                );
            }

            return ResponseEntity.ok(Map.of("success", true, "id", id));
        } catch (Exception e) {
            log.error("[Planning] Error saving target: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Delete Target ────────────────────────────────────────────────────────
    @DeleteMapping("/targets/{id}")
    @Transactional
    public ResponseEntity<?> deleteTarget(@PathVariable String id) {
        log.info("[Planning] Deleting target with ID: {}", id);
        try {
            jdbcTemplate.update("DELETE FROM planning_targets WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[Planning] Error deleting target: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Get Forecasts ────────────────────────────────────────────────────────
    @GetMapping("/forecasts")
    public ResponseEntity<?> getForecasts() {
        log.info("[Planning] Fetching forecasts");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM planning_forecasts ORDER BY forecast_period DESC"
            );
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("[Planning] Error fetching forecasts: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Get Calendar Events ──────────────────────────────────────────────────
    @GetMapping("/calendar-events")
    public ResponseEntity<?> getCalendarEvents() {
        log.info("[Planning] Fetching calendar events");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM planning_calendar_events ORDER BY event_date ASC"
            );
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("[Planning] Error fetching calendar events: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Save Calendar Event ──────────────────────────────────────────────────
    @PostMapping("/calendar-events")
    @Transactional
    public ResponseEntity<?> saveCalendarEvent(@RequestBody Map<String, Object> body) {
        log.info("[Planning] Saving calendar event: {}", body);
        try {
            String id = (String) body.get("id");
            String title = (String) body.get("title");
            String eventType = (String) body.get("eventType");
            String eventDateStr = (String) body.get("eventDate");
            String details = (String) body.get("details");

            if (title == null || eventType == null || eventDateStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            java.sql.Date eventDate = java.sql.Date.valueOf(eventDateStr);

            if (id != null && !id.isBlank()) {
                jdbcTemplate.update(
                    "UPDATE planning_calendar_events SET title = ?, event_type = ?, event_date = ?, details = ? WHERE id = ?",
                    title, eventType, eventDate, details, id
                );
            } else {
                id = "evt_" + System.currentTimeMillis();
                jdbcTemplate.update(
                    "INSERT INTO planning_calendar_events (id, title, event_type, event_date, details) VALUES (?, ?, ?, ?, ?)",
                    id, title, eventType, eventDate, details
                );
            }

            return ResponseEntity.ok(Map.of("success", true, "id", id));
        } catch (Exception e) {
            log.error("[Planning] Error saving calendar event: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
