package com.connectit.core.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import com.connectit.core.util.DbUtil;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('USER', 'AGENT', 'SUB_ADMIN', 'ADMIN', 'SUPER_ADMIN', 'ULTRA_SUPER_ADMIN')")
public class CompanyController {

    private final JdbcTemplate jdbcTemplate;

    // ── Helper: Check Admin Access ──────────────────────────────────────────
    private boolean hasAdminAccess() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_ULTRA_SUPER_ADMIN").contains(a.getAuthority()));
    }

    // ── Helper: Format Company Row with Snake & Camel Case ──────────────────
    private Map<String, Object> formatCompany(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            m.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        if (m.containsKey("id") && m.get("id") != null) {
            m.put("id", String.valueOf(m.get("id")));
        }
        mapField(m, "contact_name", "contactName");
        mapField(m, "postal_code", "postalCode");
        mapField(m, "logo_url", "logoUrl");
        mapField(m, "primary_color", "primaryColor");
        mapField(m, "secondary_color", "secondaryColor");
        mapField(m, "support_signature", "supportSignature");
        mapField(m, "priority_tier", "priorityTier");
        mapField(m, "default_assignment_group", "defaultAssignmentGroup");
        mapField(m, "default_sla_policy", "defaultSlaPolicy");
        mapField(m, "default_support_mailbox", "defaultSupportMailbox");
        mapField(m, "created_at", "createdAt");
        mapField(m, "updated_at", "updatedAt");

        if (m.containsKey("email_integration_id")) {
            Object val = m.get("email_integration_id");
            if (val != null) {
                m.put("email_integration_id", String.valueOf(val));
                m.put("emailIntegrationId", String.valueOf(val));
            } else {
                m.put("email_integration_id", "");
                m.put("emailIntegrationId", "");
            }
        }
        return m;
    }

    private void mapField(Map<String, Object> m, String snake, String camel) {
        if (m.containsKey(snake)) {
            Object val = m.get(snake);
            m.put(camel, val);
        }
    }

    // ── Helper: Format Ticket Row ───────────────────────────────────────────
    private Map<String, Object> formatTicket(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            m.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        if (m.containsKey("id") && m.get("id") != null) {
            m.put("id", String.valueOf(m.get("id")));
        }
        if (m.containsKey("created_at") && m.get("created_at") != null) {
            m.put("createdAt", String.valueOf(m.get("created_at")));
        }
        if (m.containsKey("company_id") && m.get("company_id") != null) {
            m.put("companyId", String.valueOf(m.get("company_id")));
            m.put("company_id", String.valueOf(m.get("company_id")));
        }
        return m;
    }

    private Map<String, Object> formatHistory(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            m.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        if (m.containsKey("id") && m.get("id") != null) {
            m.put("id", String.valueOf(m.get("id")));
        }
        if (m.containsKey("company_id") && m.get("company_id") != null) {
            m.put("companyId", String.valueOf(m.get("company_id")));
            m.put("company_id", String.valueOf(m.get("company_id")));
        }
        return m;
    }

    // ── Helper: Extract Fields Safely ────────────────────────────────────────
    private String getVal(Map<String, Object> body, String camelKey, String snakeKey) {
        if (body.containsKey(camelKey) && body.get(camelKey) != null) {
            return body.get(camelKey).toString();
        }
        if (body.containsKey(snakeKey) && body.get(snakeKey) != null) {
            return body.get(snakeKey).toString();
        }
        return null;
    }

    private Integer getIntVal(Map<String, Object> body, String camelKey, String snakeKey) {
        Object val = null;
        if (body.containsKey(camelKey)) {
            val = body.get(camelKey);
        } else if (body.containsKey(snakeKey)) {
            val = body.get(snakeKey);
        }
        if (val == null || val.toString().isBlank()) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helper: Log History to company_history ───────────────────────────────
    private void logHistory(Long companyId, String action, String fieldName, String oldValue, String newValue) {
        try {
            String user = SecurityContextHolder.getContext().getAuthentication().getName();
            try {
                List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT email FROM users WHERE uid = ?", user);
                if (!users.isEmpty() && users.get(0).get("email") != null) {
                    user = (String) users.get(0).get("email");
                }
            } catch (Exception ignored) {}

            jdbcTemplate.update(
                "INSERT INTO company_history (company_id, action, field_name, old_value, new_value, user, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
                companyId, action, fieldName, oldValue, newValue, user, new Timestamp(System.currentTimeMillis())
            );
        } catch (Exception e) {
            log.error("Failed to write company history: {}", e.getMessage());
        }
    }

    private void compareAndLog(Long companyId, Map<String, Object> oldMap, Map<String, Object> body, String dbField, String camelKey, String snakeKey) {
        String oldVal = oldMap.get(dbField) != null ? oldMap.get(dbField).toString() : "";
        String newVal = getVal(body, camelKey, snakeKey);
        if (newVal == null) newVal = "";
        if (!oldVal.equals(newVal)) {
            logHistory(companyId, "updated", dbField, oldVal, newVal);
        }
    }

    // ── 1. GET /api/companies (List All) ─────────────────────────────────────
    @GetMapping("/companies")
    public ResponseEntity<?> listCompanies() {
        log.info("[CompanyController] Fetching list of all companies");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM companies ORDER BY name ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(formatCompany(row));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error listing companies: ", e);
            return ResponseEntity.ok(List.of());
        }
    }

    // ── 2. GET /api/companies/{id} (Single View) ─────────────────────────────
    @GetMapping("/companies/{id}")
    public ResponseEntity<?> getCompany(@PathVariable Long id) {
        log.info("[CompanyController] Fetching company details for ID: {}", id);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM companies WHERE id = ?", id);
            if (rows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Company not found"));
            }
            return ResponseEntity.ok(formatCompany(rows.get(0)));
        } catch (Exception e) {
            log.error("Error fetching company {}: ", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── 3. GET /api/companies/{id}/tickets (Tickets View) ────────────────────
    @GetMapping("/companies/{id}/tickets")
    public ResponseEntity<?> getCompanyTickets(@PathVariable Long id) {
        log.info("[CompanyController] Fetching tickets for company ID: {}", id);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM tickets WHERE company_id = ? ORDER BY created_at DESC", id
            );
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(formatTicket(row));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching tickets for company {}: ", id, e);
            return ResponseEntity.ok(List.of());
        }
    }

    // ── 4. GET /api/companies/{id}/history (History View) ────────────────────
    @GetMapping("/companies/{id}/history")
    public ResponseEntity<?> getCompanyHistory(@PathVariable Long id) {
        log.info("[CompanyController] Fetching history for company ID: {}", id);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM company_history WHERE company_id = ? ORDER BY timestamp DESC", id
            );
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(formatHistory(row));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching history for company {}: ", id, e);
            return ResponseEntity.ok(List.of());
        }
    }

    // ── 5. POST /api/companies (Create) ──────────────────────────────────────
    @PostMapping("/companies")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> createCompany(@RequestBody Map<String, Object> body) {
        log.info("[CompanyController] Creating new company");
        try {
            String name = getVal(body, "name", "name");
            if (name == null || name.trim().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Company Name is required"));
            }

            String sql = "INSERT INTO companies (name, contact_name, phone, email, address1, address2, city, province, postal_code, country, website, logo_url, type, status, email_integration_id, primary_color, secondary_color, support_signature, industry, priority_tier, default_assignment_group, default_sla_policy, default_support_mailbox, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                ps.setString(2, getVal(body, "contactName", "contact_name"));
                ps.setString(3, getVal(body, "phone", "phone"));
                ps.setString(4, getVal(body, "email", "email"));
                ps.setString(5, getVal(body, "address1", "address1"));
                ps.setString(6, getVal(body, "address2", "address2"));
                ps.setString(7, getVal(body, "city", "city"));
                ps.setString(8, getVal(body, "province", "province"));
                ps.setString(9, getVal(body, "postalCode", "postal_code"));
                ps.setString(10, getVal(body, "country", "country"));
                ps.setString(11, getVal(body, "website", "website"));
                ps.setString(12, getVal(body, "logoUrl", "logo_url"));
                ps.setString(13, getVal(body, "type", "type"));
                ps.setString(14, getVal(body, "status", "status"));
                ps.setObject(15, getIntVal(body, "emailIntegrationId", "email_integration_id"));
                ps.setString(16, getVal(body, "primaryColor", "primary_color"));
                ps.setString(17, getVal(body, "secondaryColor", "secondary_color"));
                ps.setString(18, getVal(body, "supportSignature", "support_signature"));
                ps.setString(19, getVal(body, "industry", "industry"));
                ps.setString(20, getVal(body, "priorityTier", "priority_tier"));
                ps.setString(21, getVal(body, "defaultAssignmentGroup", "default_assignment_group"));
                ps.setString(22, getVal(body, "defaultSlaPolicy", "default_sla_policy"));
                ps.setString(23, getVal(body, "defaultSupportMailbox", "default_support_mailbox"));
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ps.setTimestamp(24, now);
                ps.setTimestamp(25, now);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);

            logHistory(newId, "created", "all", "", name);

            List<Map<String, Object>> createdRows = jdbcTemplate.queryForList("SELECT * FROM companies WHERE id = ?", newId);
            if (createdRows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to retrieve created company"));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(formatCompany(createdRows.get(0)));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "A company with this name already exists"));
        } catch (Exception e) {
            log.error("Error creating company: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── 6. PUT /api/companies/{id} (Update) ──────────────────────────────────
    @PutMapping("/companies/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> updateCompany(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("[CompanyController] Updating company ID: {}", id);
        try {
            List<Map<String, Object>> oldRows = jdbcTemplate.queryForList("SELECT * FROM companies WHERE id = ?", id);
            if (oldRows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Company not found"));
            }
            Map<String, Object> oldMap = oldRows.get(0);

            String name = getVal(body, "name", "name");
            if (name == null || name.trim().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Company Name is required"));
            }

            String sql = "UPDATE companies SET name = ?, contact_name = ?, phone = ?, email = ?, address1 = ?, address2 = ?, city = ?, province = ?, postal_code = ?, country = ?, website = ?, logo_url = ?, type = ?, status = ?, email_integration_id = ?, primary_color = ?, secondary_color = ?, support_signature = ?, industry = ?, priority_tier = ?, default_assignment_group = ?, default_sla_policy = ?, default_support_mailbox = ?, updated_at = ? WHERE id = ?";

            jdbcTemplate.update(sql,
                name,
                getVal(body, "contactName", "contact_name"),
                getVal(body, "phone", "phone"),
                getVal(body, "email", "email"),
                getVal(body, "address1", "address1"),
                getVal(body, "address2", "address2"),
                getVal(body, "city", "city"),
                getVal(body, "province", "province"),
                getVal(body, "postalCode", "postal_code"),
                getVal(body, "country", "country"),
                getVal(body, "website", "website"),
                getVal(body, "logoUrl", "logo_url"),
                getVal(body, "type", "type"),
                getVal(body, "status", "status"),
                getIntVal(body, "emailIntegrationId", "email_integration_id"),
                getVal(body, "primaryColor", "primary_color"),
                getVal(body, "secondaryColor", "secondary_color"),
                getVal(body, "supportSignature", "support_signature"),
                getVal(body, "industry", "industry"),
                getVal(body, "priorityTier", "priority_tier"),
                getVal(body, "defaultAssignmentGroup", "default_assignment_group"),
                getVal(body, "defaultSlaPolicy", "default_sla_policy"),
                getVal(body, "defaultSupportMailbox", "default_support_mailbox"),
                new Timestamp(System.currentTimeMillis()),
                id
            );

            // Compare and log history
            compareAndLog(id, oldMap, body, "name", "name", "name");
            compareAndLog(id, oldMap, body, "contact_name", "contactName", "contact_name");
            compareAndLog(id, oldMap, body, "phone", "phone", "phone");
            compareAndLog(id, oldMap, body, "email", "email", "email");
            compareAndLog(id, oldMap, body, "website", "website", "website");
            compareAndLog(id, oldMap, body, "logo_url", "logoUrl", "logo_url");
            compareAndLog(id, oldMap, body, "type", "type", "type");
            compareAndLog(id, oldMap, body, "status", "status", "status");
            compareAndLog(id, oldMap, body, "primary_color", "primaryColor", "primary_color");
            compareAndLog(id, oldMap, body, "secondary_color", "secondaryColor", "secondary_color");
            compareAndLog(id, oldMap, body, "support_signature", "supportSignature", "support_signature");
            compareAndLog(id, oldMap, body, "industry", "industry", "industry");
            compareAndLog(id, oldMap, body, "priority_tier", "priorityTier", "priority_tier");
            compareAndLog(id, oldMap, body, "default_assignment_group", "defaultAssignmentGroup", "default_assignment_group");
            compareAndLog(id, oldMap, body, "default_sla_policy", "defaultSlaPolicy", "default_sla_policy");
            compareAndLog(id, oldMap, body, "default_support_mailbox", "defaultSupportMailbox", "default_support_mailbox");
            compareAndLog(id, oldMap, body, "email_integration_id", "emailIntegrationId", "email_integration_id");

            List<Map<String, Object>> updatedRows = jdbcTemplate.queryForList("SELECT * FROM companies WHERE id = ?", id);
            return ResponseEntity.ok(formatCompany(updatedRows.get(0)));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "A company with this name already exists"));
        } catch (Exception e) {
            log.error("Error updating company {}: ", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── 7. DELETE /api/companies/{id} (Delete) ───────────────────────────────
    @DeleteMapping("/companies/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> deleteCompany(@PathVariable Long id) {
        log.info("[CompanyController] Deleting company ID: {}", id);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT name FROM companies WHERE id = ?", id);
            if (rows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Company not found"));
            }
            String name = (String) rows.get(0).get("name");

            jdbcTemplate.update("DELETE FROM companies WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM company_history WHERE company_id = ?", id);

            logHistory(id, "deleted", "all", name, "");

            return ResponseEntity.ok(Map.of("message", "Company deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting company {}: ", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
