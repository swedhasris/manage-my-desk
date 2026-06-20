package com.connectit.core.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.connectit.core.util.DbUtil;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MasterController {

    private static final Logger log = LoggerFactory.getLogger(MasterController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> VALID_MASTER_TABLES = List.of(
        "mst_groups", "mst_statuses", "mst_roles", "mst_departments",
        "mst_ticket_types", "mst_projects", "mst_priorities",
        "mst_sources", "mst_tags", "mst_categories", "mst_subcategories",
        "mst_providences", "mst_members"
    );

    // ── Helper to check Admin Access ──────────────────────────────────────────
    private boolean checkAdminAccess(String uid, String email) {
        List<String> fallbackEmails = List.of("arun.g@technosprint.net", "swedhasris@gmail.com", "ulter@technosprint.net", "admin@technosprint.net");
        if (email != null && fallbackEmails.contains(email.toLowerCase().trim())) {
            return true;
        }
        if (uid == null || uid.isBlank()) {
            return false;
        }
        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT role, email FROM users WHERE uid = ?", uid);
            if (!users.isEmpty()) {
                Map<String, Object> user = users.get(0);
                String role = (String) user.get("role");
                String userEmail = (String) user.get("email");
                if (List.of("admin", "super_admin", "ultra_super_admin").contains(role) ||
                    (userEmail != null && fallbackEmails.contains(userEmail.toLowerCase().trim()))) {
                    return true;
                }
            }
        } catch (Exception err) {
            System.err.println("Error checking admin access: " + err.getMessage());
        }
        return false;
    }

    private boolean isAuthorized(String headerUid, String headerEmail, String queryUid, String queryEmail) {
        String uid = headerUid != null ? headerUid : queryUid;
        String email = headerEmail != null ? headerEmail : queryEmail;
        if (uid == null || uid.isBlank()) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                uid = auth.getName();
            }
        }
        return checkAdminAccess(uid, email);
    }

    // ── Cache-Control header helper ────────────────────────────────────────────
    private void addCacheHeaders(HttpServletResponse response, int maxAgeSeconds) {
        response.setHeader("Cache-Control", "public, max-age=" + maxAgeSeconds + ", stale-while-revalidate=" + (maxAgeSeconds * 2));
    }

    // ── Batch init-data endpoint (loads all dropdowns in one request) ──────────
    @GetMapping("/init-data")
    public ResponseEntity<?> getInitData(HttpServletResponse response) {
        try {
            addCacheHeaders(response, 30);
            Map<String, Object> result = new LinkedHashMap<>();

            // Companies
            try {
                result.put("companies", stringifyIds(
                    jdbcTemplate.queryForList("SELECT id, name FROM companies ORDER BY name ASC")
                ));
            } catch (Exception e) { result.put("companies", List.of()); }

            // Incident categories (active only)
            try {
                result.put("incidentCategories", stringifyIds(
                    jdbcTemplate.queryForList("SELECT * FROM incident_categories WHERE status = 'Active' ORDER BY name ASC")
                ));
            } catch (Exception e) { result.put("incidentCategories", List.of()); }

            // Incident category options (active only)
            try {
                result.put("incidentCategoryOptions", stringifyIds(
                    jdbcTemplate.queryForList("SELECT * FROM incident_category_options WHERE status = 'Active' ORDER BY value_text ASC")
                ));
            } catch (Exception e) { result.put("incidentCategoryOptions", List.of()); }

            // Users (lightweight — name, email, role only)
            try {
                result.put("users", stringifyIds(
                    jdbcTemplate.queryForList("SELECT id, uid, name, email, role, phone FROM users WHERE is_active = 1 ORDER BY name ASC")
                ));
            } catch (Exception e) { result.put("users", List.of()); }

            // Groups
            try {
                result.put("groups", stringifyIds(
                    jdbcTemplate.queryForList("SELECT id, name, description FROM settings_groups ORDER BY name ASC")
                ));
            } catch (Exception e) { result.put("groups", List.of()); }

            // Active custom dropdowns
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, name, label, options_json, enabled_for_all, is_required FROM custom_dropdowns WHERE is_active = 1 ORDER BY created_at ASC"
                );
                List<Map<String, Object>> dropdowns = new ArrayList<>();
                for (Map<String, Object> r : rows) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.get("id"));
                    m.put("name", r.get("name"));
                    m.put("label", r.get("label"));
                    m.put("options", parseJsonArray((String) r.get("options_json")));
                    m.put("enabledForAll", parseBoolean(r.get("enabled_for_all")));
                    m.put("isRequired", parseBoolean(r.get("is_required")));
                    dropdowns.add(m);
                }
                result.put("customDropdowns", dropdowns);
            } catch (Exception e) { result.put("customDropdowns", List.of()); }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Custom Dropdowns Endpoints ────────────────────────────────────────────
    @GetMapping("/custom-dropdowns")
    public ResponseEntity<?> getCustomDropdowns(HttpServletResponse response) {
        addCacheHeaders(response, 30);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM custom_dropdowns ORDER BY created_at ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.get("id"));
                m.put("name", r.get("name"));
                m.put("label", r.get("label"));
                m.put("options", parseJsonArray((String) r.get("options_json")));
                m.put("enabledForAll", parseBoolean(r.get("enabled_for_all")));
                m.put("enabledCompanyIds", parseJsonArray((String) r.get("enabled_company_ids_json")));
                m.put("isRequired", parseBoolean(r.get("is_required")));
                m.put("isActive", parseBoolean(r.get("is_active")));
                m.put("createdAt", r.get("created_at"));
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/custom-dropdowns")
    @Transactional
    public ResponseEntity<?> createCustomDropdown(@RequestBody Map<String, Object> body) {
        try {
            String id = "dd_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            String name = (String) body.get("name");
            String label = (String) body.get("label");
            Object options = body.getOrDefault("options", List.of());
            boolean enabledForAll = parseBoolean(body.getOrDefault("enabledForAll", true));
            Object enabledCompanyIds = body.getOrDefault("enabledCompanyIds", List.of());
            boolean isRequired = parseBoolean(body.getOrDefault("isRequired", false));
            boolean isActive = parseBoolean(body.getOrDefault("isActive", true));

            jdbcTemplate.update(
                "INSERT INTO custom_dropdowns (id, name, label, options_json, enabled_for_all, enabled_company_ids_json, is_required, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, label, toJsonString(options), enabledForAll ? 1 : 0, toJsonString(enabledCompanyIds), isRequired ? 1 : 0, isActive ? 1 : 0
            );

            Map<String, Object> res = new HashMap<>();
            res.put("id", id);
            res.put("name", name);
            res.put("label", label);
            res.put("options", options);
            res.put("enabledForAll", enabledForAll);
            res.put("enabledCompanyIds", enabledCompanyIds);
            res.put("isRequired", isRequired);
            res.put("isActive", isActive);
            res.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/custom-dropdowns/{id}")
    @Transactional
    public ResponseEntity<?> updateCustomDropdown(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String label = (String) body.get("label");
            Object options = body.getOrDefault("options", List.of());
            boolean enabledForAll = parseBoolean(body.getOrDefault("enabledForAll", true));
            Object enabledCompanyIds = body.getOrDefault("enabledCompanyIds", List.of());
            boolean isRequired = parseBoolean(body.getOrDefault("isRequired", false));
            boolean isActive = parseBoolean(body.getOrDefault("isActive", true));

            String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            jdbcTemplate.update(
                "UPDATE custom_dropdowns SET name=?, label=?, options_json=?, enabled_for_all=?, enabled_company_ids_json=?, is_required=?, is_active=?, updated_at=? WHERE id=?",
                name, label, toJsonString(options), enabledForAll ? 1 : 0, toJsonString(enabledCompanyIds), isRequired ? 1 : 0, isActive ? 1 : 0, nowStr, id
            );

            Map<String, Object> res = new HashMap<>();
            res.put("id", id);
            res.put("name", name);
            res.put("label", label);
            res.put("options", options);
            res.put("enabledForAll", enabledForAll);
            res.put("enabledCompanyIds", enabledCompanyIds);
            res.put("isRequired", isRequired);
            res.put("isActive", isActive);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/custom-dropdowns/{id}")
    @Transactional
    public ResponseEntity<?> deleteCustomDropdown(@PathVariable String id) {
        try {
            jdbcTemplate.update("DELETE FROM custom_dropdowns WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/custom-dropdowns/active")
    public ResponseEntity<?> getActiveCustomDropdowns(@RequestParam(required = false) String company_id) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM custom_dropdowns WHERE is_active = 1 ORDER BY created_at ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                boolean enabledForAll = parseBoolean(r.get("enabled_for_all"));
                List<?> enabledCompanyIds = parseJsonArray((String) r.get("enabled_company_ids_json"));

                if (company_id == null && enabledForAll) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.get("id"));
                    m.put("name", r.get("name"));
                    m.put("label", r.get("label"));
                    m.put("options", parseJsonArray((String) r.get("options_json")));
                    m.put("enabledForAll", enabledForAll);
                    m.put("isRequired", parseBoolean(r.get("is_required")));
                    result.add(m);
                } else if (company_id != null && (enabledForAll || enabledCompanyIds.contains(company_id))) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.get("id"));
                    m.put("name", r.get("name"));
                    m.put("label", r.get("label"));
                    m.put("options", parseJsonArray((String) r.get("options_json")));
                    m.put("enabledForAll", enabledForAll);
                    m.put("isRequired", parseBoolean(r.get("is_required")));
                    result.add(m);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Feature Permissions API ───────────────────────────────────────────────
    @GetMapping("/feature-permissions")
    public ResponseEntity<?> getFeaturePermissions(@RequestParam(required = false) String company_id) {
        try {
            if (company_id == null || company_id.trim().isEmpty() || "undefined".equals(company_id) || "null".equals(company_id)) {
                return ResponseEntity.ok(new ArrayList<>());
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM company_feature_permissions WHERE company_id = ?", company_id);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("companyId", r.get("company_id"));
                m.put("featureId", r.get("feature_id"));
                m.put("canView", parseBoolean(r.get("can_view")));
                m.put("canUse", parseBoolean(r.get("can_use")));
                m.put("canEdit", parseBoolean(r.get("can_edit")));
                m.put("isMandatory", parseBoolean(r.get("is_mandatory")));
                m.put("status", r.get("status") != null ? r.get("status") : "enabled");
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving company feature permissions: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ArrayList<>()); // Fallback: return HTTP 200 and [] instead of 500
        }
    }

    @PostMapping("/feature-permissions")
    @Transactional
    public ResponseEntity<?> saveFeaturePermissions(@RequestBody Map<String, Object> body) {
        try {
            String companyId = (String) body.get("companyId");
            String featureId = (String) body.get("featureId");
            boolean canView = parseBoolean(body.get("canView"));
            boolean canUse = parseBoolean(body.get("canUse"));
            boolean canEdit = parseBoolean(body.get("canEdit"));
            boolean isMandatory = parseBoolean(body.get("isMandatory"));
            String status = (String) body.get("status");

            if (companyId == null || featureId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM company_feature_permissions WHERE company_id = ? AND feature_id = ?", companyId, featureId
            );

            if (!existing.isEmpty()) {
                jdbcTemplate.update(
                    "UPDATE company_feature_permissions SET can_view=?, can_use=?, can_edit=?, is_mandatory=?, status=?, updated_at=CURRENT_TIMESTAMP WHERE company_id=? AND feature_id=?",
                    canView ? 1 : 0, canUse ? 1 : 0, canEdit ? 1 : 0, isMandatory ? 1 : 0, status != null ? status : "enabled", companyId, featureId
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO company_feature_permissions (company_id, feature_id, can_view, can_use, can_edit, is_mandatory, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    companyId, featureId, canView ? 1 : 0, canUse ? 1 : 0, canEdit ? 1 : 0, isMandatory ? 1 : 0, status != null ? status : "enabled"
                );
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error saving company feature permissions: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Incident Categories API ───────────────────────────────────────────────
    @GetMapping("/incident-categories")
    public ResponseEntity<?> getIncidentCategories(
            @RequestParam(required = false, name = "active_only") String activeOnlyStr,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail,
            HttpServletResponse response) {
        
        try {
            boolean activeOnly = "true".equalsIgnoreCase(activeOnlyStr);
            if (activeOnly) {
                // Public read-only — allow browser cache for 30s
                addCacheHeaders(response, 30);
            }
            if (!activeOnly) {
                if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
                }
            }

            String sql = "SELECT * FROM incident_categories";
            if (activeOnly) {
                sql += " WHERE status = 'Active'";
            }
            sql += " ORDER BY name ASC";

            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
            return ResponseEntity.ok(stringifyIds(list));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/incident-categories")
    @Transactional
    public ResponseEntity<?> createIncidentCategory(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail) {
        
        try {
            if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
            }

            String name = (String) body.get("name");
            String description = (String) body.get("description");
            String status = (String) body.getOrDefault("status", "Active");
            String createdBy = (String) body.get("created_by");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
            }

            name = name.trim();
            List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id FROM incident_categories WHERE LOWER(name) = ?", name.toLowerCase());
            if (!existing.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "This category already exists"));
            }

            String insertSql = "INSERT INTO incident_categories (name, description, status, created_by, last_updated_by) VALUES (?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            String finalName = name;
            String finalCreatedBy = createdBy != null ? createdBy : "Admin";
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, finalName);
                ps.setString(2, description != null ? description : "");
                ps.setString(3, status);
                ps.setString(4, finalCreatedBy);
                ps.setString(5, finalCreatedBy);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);

            Map<String, Object> res = new HashMap<>();
            res.put("id", String.valueOf(newId));
            res.put("name", name);
            res.put("description", description);
            res.put("status", status);
            res.put("created_by", finalCreatedBy);
            res.put("message", "Incident category created successfully");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/incident-categories/{id}")
    @Transactional
    public ResponseEntity<?> updateIncidentCategory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail) {
        
        try {
            if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
            }

            String name = (String) body.get("name");
            String description = (String) body.get("description");
            String status = (String) body.getOrDefault("status", "Active");
            String lastUpdatedBy = (String) body.get("last_updated_by");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
            }

            name = name.trim();
            List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id FROM incident_categories WHERE LOWER(name) = ? AND id != ?", name.toLowerCase(), id);
            if (!existing.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "This category already exists"));
            }

            String updateSql = "UPDATE incident_categories SET name = ?, description = ?, status = ?, last_updated_by = ?, last_updated_date = CURRENT_TIMESTAMP WHERE id = ?";
            jdbcTemplate.update(updateSql, name, description != null ? description : "", status, lastUpdatedBy != null ? lastUpdatedBy : "Admin", id);

            Map<String, Object> res = new HashMap<>();
            res.put("id", String.valueOf(id));
            res.put("name", name);
            res.put("description", description);
            res.put("status", status);
            res.put("last_updated_by", lastUpdatedBy);
            res.put("message", "Incident category updated successfully");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/incident-categories/{id}")
    @Transactional
    public ResponseEntity<?> deleteIncidentCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail) {
        
        try {
            if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
            }

            List<Map<String, Object>> categories = jdbcTemplate.queryForList("SELECT name FROM incident_categories WHERE id = ?", id);
            if (categories.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String categoryName = (String) categories.get(0).get("name");

            // Integrity check
            String checkSql = "SELECT COUNT(*) as count FROM tickets WHERE (incident_category = ? OR category = ?) AND status NOT IN ('Resolved', 'Closed', 'Canceled')";
            Map<String, Object> checkResult = jdbcTemplate.queryForMap(checkSql, categoryName, categoryName);
            long activeCount = ((Number) checkResult.get("count")).longValue();

            if (activeCount > 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "This category is currently used by existing tickets"));
            }

            jdbcTemplate.update("DELETE FROM incident_categories WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Incident category deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Incident Category Options API ─────────────────────────────────────────
    @GetMapping("/incident-categories/options")
    public ResponseEntity<?> getIncidentCategoryOptions(
            @RequestParam(required = false) Integer category_id,
            @RequestParam(required = false, name = "active_only") String activeOnlyStr) {
        
        try {
            boolean activeOnly = "true".equalsIgnoreCase(activeOnlyStr);
            String sql = "SELECT * FROM incident_category_options";
            List<Object> params = new ArrayList<>();
            List<String> clauses = new ArrayList<>();

            if (category_id != null) {
                clauses.add("category_id = ?");
                params.add(category_id);
            }
            if (activeOnly) {
                clauses.add("status = 'Active'");
            }

            if (!clauses.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", clauses);
            }
            sql += " ORDER BY value_text ASC";

            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, params.toArray());
            return ResponseEntity.ok(stringifyIds(list));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/incident-categories/options")
    @Transactional
    public ResponseEntity<?> createIncidentCategoryOption(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail) {
        
        try {
            if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
            }

            Object categoryIdObj = body.get("category_id");
            String valueText = (String) body.get("value_text");
            String status = (String) body.getOrDefault("status", "Active");
            String createdBy = (String) body.get("created_by");

            if (categoryIdObj == null || valueText == null || valueText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Category ID and value text are required"));
            }

            int categoryId = Integer.parseInt(String.valueOf(categoryIdObj));
            valueText = valueText.trim();

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM incident_category_options WHERE category_id = ? AND LOWER(value_text) = ?", categoryId, valueText.toLowerCase()
            );
            if (!existing.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "This value already exists in this category"));
            }

            String insertSql = "INSERT INTO incident_category_options (category_id, value_text, status, created_by, last_updated_by) VALUES (?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            String finalValueText = valueText;
            String finalCreatedBy = createdBy != null ? createdBy : "Admin";
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, categoryId);
                ps.setString(2, finalValueText);
                ps.setString(3, status);
                ps.setString(4, finalCreatedBy);
                ps.setString(5, finalCreatedBy);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);

            Map<String, Object> res = new HashMap<>();
            res.put("id", String.valueOf(newId));
            res.put("category_id", categoryId);
            res.put("value_text", valueText);
            res.put("status", status);
            res.put("message", "Value added successfully");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/incident-categories/options/{id}")
    @Transactional
    public ResponseEntity<?> updateIncidentCategoryOption(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail) {
        
        try {
            if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
            }

            String valueText = (String) body.get("value_text");
            String status = (String) body.getOrDefault("status", "Active");
            String lastUpdatedBy = (String) body.get("last_updated_by");

            if (valueText == null || valueText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Value text is required"));
            }

            valueText = valueText.trim();
            jdbcTemplate.update(
                "UPDATE incident_category_options SET value_text = ?, status = ?, last_updated_by = ?, last_updated_date = CURRENT_TIMESTAMP WHERE id = ?",
                valueText, status, lastUpdatedBy != null ? lastUpdatedBy : "Admin", id
            );

            Map<String, Object> res = new HashMap<>();
            res.put("id", String.valueOf(id));
            res.put("value_text", valueText);
            res.put("status", status);
            res.put("message", "Value updated successfully");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/incident-categories/options/{id}")
    @Transactional
    public ResponseEntity<?> deleteIncidentCategoryOption(
            @PathVariable Long id,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String email,
            @RequestHeader(required = false, name = "x-user-uid") String headerUid,
            @RequestHeader(required = false, name = "x-user-email") String headerEmail) {
        
        try {
            if (!isAuthorized(headerUid, headerEmail, uid, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: Unauthorized role"));
            }

            jdbcTemplate.update("DELETE FROM incident_category_options WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Value deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Dynamic Master-Data Endpoints ─────────────────────────────────────────
    @GetMapping("/master-data/{table}")
    public ResponseEntity<?> getMasterData(
            @PathVariable String table,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "ASC") String order,
            @RequestParam(required = false) Integer category_id,
            @RequestParam(required = false) Integer subcategory_id,
            @RequestParam(required = false) Integer group_id,
            HttpServletResponse response) {
        
        try {
            if (!VALID_MASTER_TABLES.contains(table)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid master table"));
            }

            // Cache simple reads (no search) for 30 seconds
            if (search == null) {
                addCacheHeaders(response, 30);
            }

            String sql = "SELECT * FROM " + table + " WHERE 1=1";
            List<Object> params = new ArrayList<>();

            if (status != null) {
                sql += " AND status = ?";
                params.add(status);
            }

            if (search != null) {
                sql += " AND (name LIKE ? OR description LIKE ?)";
                params.add("%" + search + "%");
                params.add("%" + search + "%");
            }

            if (category_id != null && "mst_subcategories".equals(table)) {
                sql += " AND category_id = ?";
                params.add(category_id);
            }
            if (subcategory_id != null && "mst_providences".equals(table)) {
                sql += " AND subcategory_id = ?";
                params.add(subcategory_id);
            }
            if (group_id != null && "mst_members".equals(table)) {
                sql += " AND group_id = ?";
                params.add(group_id);
            }

            // Safe sorting
            List<String> allowedSort = List.of("name", "created_at", "id", "level", "status");
            String finalSort = allowedSort.contains(sort) ? sort : "name";
            String finalOrder = "DESC".equalsIgnoreCase(order) ? "DESC" : "ASC";

            sql += " ORDER BY " + finalSort + " " + finalOrder;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            return ResponseEntity.ok(stringifyIds(rows));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/master-data/{table}")
    @Transactional
    public ResponseEntity<?> createMasterData(@PathVariable String table, @RequestBody Map<String, Object> body) {
        try {
            if (!VALID_MASTER_TABLES.contains(table)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid master table"));
            }

            List<String> fields = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (!"id".equals(entry.getKey())) {
                    fields.add(entry.getKey());
                    placeholders.add("?");
                    values.add(entry.getValue());
                }
            }

            if (fields.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Empty body"));
            }

            String insertSql = "INSERT INTO " + table + " (" + String.join(", ", fields) + ") VALUES (" + String.join(", ", placeholders) + ")";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < values.size(); i++) {
                    ps.setObject(i + 1, values.get(i));
                }
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table + " WHERE id = ?", newId);
            if (rows.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(stringifyId(new HashMap<>(rows.get(0))));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "An entry with this name already exists"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/master-data/{table}/{id}")
    @Transactional
    public ResponseEntity<?> updateMasterData(@PathVariable String table, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            if (!VALID_MASTER_TABLES.contains(table)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid master table"));
            }

            List<String> fields = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (!"id".equals(entry.getKey()) && !"created_at".equals(entry.getKey())) {
                    fields.add(entry.getKey() + " = ?");
                    values.add(entry.getValue());
                }
            }

            if (fields.isEmpty()) {
                List<Map<String, Object>> current = jdbcTemplate.queryForList("SELECT * FROM " + table + " WHERE id = ?", id);
                return ResponseEntity.ok(current.isEmpty() ? Map.of() : stringifyId(new HashMap<>(current.get(0))));
            }

            String updateSql = "UPDATE " + table + " SET " + String.join(", ", fields) + " WHERE id = ?";
            values.add(id);
            jdbcTemplate.update(updateSql, values.toArray());

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table + " WHERE id = ?", id);
            if (rows.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(stringifyId(new HashMap<>(rows.get(0))));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "An entry with this name already exists"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/master-data/{table}/{id}")
    @Transactional
    public ResponseEntity<?> deleteMasterData(
            @PathVariable String table,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") String permanent) {
        
        try {
            if (!VALID_MASTER_TABLES.contains(table)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid master table"));
            }

            if ("true".equalsIgnoreCase(permanent)) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", id);
                return ResponseEntity.ok(Map.of("message", "Item deleted permanently"));
            } else {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT status FROM " + table + " WHERE id = ?", id);
                if (rows.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                String currentStatus = (String) rows.get(0).get("status");
                String newStatus = "active".equalsIgnoreCase(currentStatus) ? "inactive" : "active";
                jdbcTemplate.update("UPDATE " + table + " SET status = ? WHERE id = ?", newStatus, id);
                return ResponseEntity.ok(Map.of("message", "Item marked as " + newStatus, "status", newStatus));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /* Commented out duplicate settings endpoints to avoid collision with AiActivityController.java
    // ── Settings Categories CRUD (mst_categories) ──────────────────────────
    @GetMapping("/settings_categories")
    public ResponseEntity<?> getSettingsCategories() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM mst_categories ORDER BY created_at ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", String.valueOf(r.get("id")));
                m.put("name", r.get("name"));
                m.put("description", r.get("description"));
                m.put("status", r.get("status"));
                m.put("createdAt", r.get("created_at"));
                m.put("createdBy", r.get("created_by"));
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/settings_categories")
    @Transactional
    public ResponseEntity<?> createSettingsCategory(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String description = (String) body.get("description");
            String status = (String) body.getOrDefault("status", "active");
            String createdBy = (String) body.getOrDefault("createdBy", "System");

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO mst_categories (name, description, status, created_by) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setString(3, status);
                ps.setString(4, createdBy);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM mst_categories WHERE id = ?", newId);
            if (rows.isEmpty()) return ResponseEntity.ok(Map.of("id", String.valueOf(newId)));
            Map<String, Object> r = rows.get(0);
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.get("id")));
            m.put("name", r.get("name"));
            m.put("description", r.get("description"));
            m.put("status", r.get("status"));
            m.put("createdAt", r.get("created_at"));
            m.put("createdBy", r.get("created_by"));
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/settings_categories/{id}")
    @Transactional
    public ResponseEntity<?> updateSettingsCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            List<String> fields = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            if (body.containsKey("name")) { fields.add("name = ?"); values.add(body.get("name")); }
            if (body.containsKey("description")) { fields.add("description = ?"); values.add(body.get("description")); }
            if (body.containsKey("status")) { fields.add("status = ?"); values.add(body.get("status")); }
            if (!fields.isEmpty()) {
                values.add(id);
                jdbcTemplate.update("UPDATE mst_categories SET " + String.join(", ", fields) + " WHERE id = ?", values.toArray());
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM mst_categories WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> r = rows.get(0);
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.get("id")));
            m.put("name", r.get("name"));
            m.put("description", r.get("description"));
            m.put("status", r.get("status"));
            m.put("createdAt", r.get("created_at"));
            m.put("createdBy", r.get("created_by"));
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_categories/{id}")
    @Transactional
    public ResponseEntity<?> deleteSettingsCategory(@PathVariable Long id) {
        try {
            jdbcTemplate.update("DELETE FROM mst_categories WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Settings Subcategories CRUD (mst_subcategories) ─────────────────────
    @GetMapping("/settings_subcategories")
    public ResponseEntity<?> getSettingsSubcategories() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.*, c.name AS category_name FROM mst_subcategories s LEFT JOIN mst_categories c ON s.category_id = c.id ORDER BY s.created_at ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", String.valueOf(r.get("id")));
                m.put("name", r.get("name"));
                m.put("description", r.get("description"));
                m.put("categoryId", String.valueOf(r.get("category_id")));
                m.put("categoryName", r.get("category_name"));
                m.put("status", r.get("status"));
                m.put("createdAt", r.get("created_at"));
                m.put("createdBy", r.get("created_by"));
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/settings_subcategories")
    @Transactional
    public ResponseEntity<?> createSettingsSubcategory(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String description = (String) body.get("description");
            Object categoryIdObj = body.get("categoryId");
            int categoryId = categoryIdObj != null ? Integer.parseInt(String.valueOf(categoryIdObj)) : 0;
            String status = (String) body.getOrDefault("status", "active");
            String createdBy = (String) body.getOrDefault("createdBy", "System");

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO mst_subcategories (name, category_id, description, status, created_by) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                ps.setInt(2, categoryId);
                ps.setString(3, description);
                ps.setString(4, status);
                ps.setString(5, createdBy);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(newId));
            m.put("name", name);
            m.put("categoryId", String.valueOf(categoryId));
            m.put("status", status);
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/settings_subcategories/{id}")
    @Transactional
    public ResponseEntity<?> updateSettingsSubcategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            List<String> fields = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            if (body.containsKey("name")) { fields.add("name = ?"); values.add(body.get("name")); }
            if (body.containsKey("description")) { fields.add("description = ?"); values.add(body.get("description")); }
            if (body.containsKey("categoryId")) { fields.add("category_id = ?"); values.add(Integer.parseInt(String.valueOf(body.get("categoryId")))); }
            if (body.containsKey("status")) { fields.add("status = ?"); values.add(body.get("status")); }
            if (!fields.isEmpty()) {
                values.add(id);
                jdbcTemplate.update("UPDATE mst_subcategories SET " + String.join(", ", fields) + " WHERE id = ?", values.toArray());
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.*, c.name AS category_name FROM mst_subcategories s LEFT JOIN mst_categories c ON s.category_id = c.id WHERE s.id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> r = rows.get(0);
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(r.get("id")));
            m.put("name", r.get("name"));
            m.put("description", r.get("description"));
            m.put("categoryId", String.valueOf(r.get("category_id")));
            m.put("categoryName", r.get("category_name"));
            m.put("status", r.get("status"));
            m.put("createdAt", r.get("created_at"));
            m.put("createdBy", r.get("created_by"));
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_subcategories/{id}")
    @Transactional
    public ResponseEntity<?> deleteSettingsSubcategory(@PathVariable Long id) {
        try {
            jdbcTemplate.update("DELETE FROM mst_subcategories WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Settings Service Providers CRUD (mst_providences) ───────────────────
    @GetMapping("/settings_service_providers")
    public ResponseEntity<?> getSettingsServiceProviders() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM mst_providences ORDER BY created_at ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", String.valueOf(r.get("id")));
                m.put("name", r.get("name"));
                // Parse SLA from description if encoded as [SLA:value] prefix
                String desc = r.get("description") != null ? r.get("description").toString() : "";
                String sla = "";
                if (desc.startsWith("[SLA:")) {
                    int end = desc.indexOf("]");
                    if (end > 5) {
                        sla = desc.substring(5, end);
                        desc = desc.substring(end + 1).trim();
                    }
                }
                m.put("description", desc);
                m.put("sla", sla);
                m.put("subcategoryId", String.valueOf(r.get("subcategory_id")));
                // Derive categoryId by looking up the subcategory's parent
                Object subId = r.get("subcategory_id");
                String categoryId = "";
                if (subId != null) {
                    try {
                        List<Map<String, Object>> subRows = jdbcTemplate.queryForList("SELECT category_id FROM mst_subcategories WHERE id = ?", subId);
                        if (!subRows.isEmpty()) categoryId = String.valueOf(subRows.get(0).get("category_id"));
                    } catch (Exception ignored) {}
                }
                m.put("categoryId", categoryId);
                m.put("status", r.get("status"));
                m.put("createdAt", r.get("created_at"));
                m.put("createdBy", r.get("created_by"));
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/settings_service_providers")
    @Transactional
    public ResponseEntity<?> createSettingsServiceProvider(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String description = (String) body.getOrDefault("description", "");
            String sla = (String) body.getOrDefault("sla", "");
            // Encode SLA into description
            String storedDesc = (sla != null && !sla.isEmpty()) ? "[SLA:" + sla + "] " + description : description;
            Object subcategoryIdObj = body.get("subcategoryId");
            int subcategoryId = subcategoryIdObj != null ? Integer.parseInt(String.valueOf(subcategoryIdObj)) : 0;
            String status = (String) body.getOrDefault("status", "active");
            String createdBy = (String) body.getOrDefault("createdBy", "System");

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO mst_providences (name, subcategory_id, description, status, created_by) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                ps.setInt(2, subcategoryId);
                ps.setString(3, storedDesc);
                ps.setString(4, status);
                ps.setString(5, createdBy);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(newId));
            m.put("name", name);
            m.put("subcategoryId", String.valueOf(subcategoryId));
            m.put("sla", sla);
            m.put("status", status);
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/settings_service_providers/{id}")
    @Transactional
    public ResponseEntity<?> updateSettingsServiceProvider(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            List<String> fields = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            if (body.containsKey("name")) { fields.add("name = ?"); values.add(body.get("name")); }
            if (body.containsKey("subcategoryId")) { fields.add("subcategory_id = ?"); values.add(Integer.parseInt(String.valueOf(body.get("subcategoryId")))); }
            if (body.containsKey("status")) { fields.add("status = ?"); values.add(body.get("status")); }
            if (body.containsKey("description") || body.containsKey("sla")) {
                String desc = body.containsKey("description") ? String.valueOf(body.getOrDefault("description", "")) : "";
                String sla = body.containsKey("sla") ? String.valueOf(body.getOrDefault("sla", "")) : "";
                String storedDesc = (sla != null && !sla.isEmpty()) ? "[SLA:" + sla + "] " + desc : desc;
                fields.add("description = ?");
                values.add(storedDesc);
            }
            if (!fields.isEmpty()) {
                values.add(id);
                jdbcTemplate.update("UPDATE mst_providences SET " + String.join(", ", fields) + " WHERE id = ?", values.toArray());
            }
            return ResponseEntity.ok(Map.of("success", true, "id", String.valueOf(id)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_service_providers/{id}")
    @Transactional
    public ResponseEntity<?> deleteSettingsServiceProvider(@PathVariable Long id) {
        try {
            jdbcTemplate.update("DELETE FROM mst_providences WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Settings Group Members CRUD (mst_members + users join) ──────────────
    @GetMapping("/settings_group_members")
    public ResponseEntity<?> getSettingsGroupMembers() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.*, u.name AS user_name, u.email AS user_email " +
                "FROM mst_members m LEFT JOIN users u ON m.user_id = u.uid " +
                "ORDER BY m.created_at ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", String.valueOf(r.get("id")));
                m.put("userId", r.get("user_id") != null ? String.valueOf(r.get("user_id")) : "");
                m.put("userName", r.get("user_name") != null ? r.get("user_name") : "");
                m.put("userEmail", r.get("user_email") != null ? r.get("user_email") : "");
                m.put("groupId", String.valueOf(r.get("group_id")));
                m.put("roleInGroup", r.get("role") != null ? r.get("role") : "Support Agent");
                m.put("isPrimary", false);
                m.put("availabilityStatus", "available");
                m.put("currentWorkload", 0);
                m.put("skills", List.of());
                m.put("status", r.get("status"));
                m.put("createdAt", r.get("created_at"));
                m.put("createdBy", r.get("created_by"));
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/settings_group_members")
    @Transactional
    public ResponseEntity<?> createSettingsGroupMember(@RequestBody Map<String, Object> body) {
        try {
            String userId = (String) body.get("userId");
            Object groupIdObj = body.get("groupId");
            int groupId = groupIdObj != null ? Integer.parseInt(String.valueOf(groupIdObj)) : 0;
            String role = (String) body.getOrDefault("roleInGroup", "Support Agent");
            String status = (String) body.getOrDefault("status", "active");
            String createdBy = (String) body.getOrDefault("createdBy", "System");

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO mst_members (user_id, group_id, role, status, created_by) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, userId);
                ps.setInt(2, groupId);
                ps.setString(3, role);
                ps.setString(4, status);
                ps.setString(5, createdBy);
                return ps;
            }, keyHolder);

            long newId = DbUtil.getGeneratedId(keyHolder);
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(newId));
            m.put("userId", userId);
            m.put("groupId", String.valueOf(groupId));
            m.put("roleInGroup", role);
            m.put("status", status);
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/settings_group_members/{id}")
    @Transactional
    public ResponseEntity<?> updateSettingsGroupMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            List<String> fields = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            if (body.containsKey("userId")) { fields.add("user_id = ?"); values.add(body.get("userId")); }
            if (body.containsKey("groupId")) { fields.add("group_id = ?"); values.add(Integer.parseInt(String.valueOf(body.get("groupId")))); }
            if (body.containsKey("roleInGroup")) { fields.add("role = ?"); values.add(body.get("roleInGroup")); }
            if (body.containsKey("status")) { fields.add("status = ?"); values.add(body.get("status")); }
            if (!fields.isEmpty()) {
                values.add(id);
                jdbcTemplate.update("UPDATE mst_members SET " + String.join(", ", fields) + " WHERE id = ?", values.toArray());
            }
            return ResponseEntity.ok(Map.of("success", true, "id", String.valueOf(id)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_group_members/{id}")
    @Transactional
    public ResponseEntity<?> deleteSettingsGroupMember(@PathVariable Long id) {
        try {
            jdbcTemplate.update("DELETE FROM mst_members WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    */

    // ── Parsing Utilities ─────────────────────────────────────────────────────
    private List<?> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private boolean parseBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        String s = val.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
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
