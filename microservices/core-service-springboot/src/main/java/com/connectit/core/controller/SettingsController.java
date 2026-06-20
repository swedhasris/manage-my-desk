package com.connectit.core.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Helper methods to extract values safely ──
    private String getStr(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }

    private Long getLong(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null) {
                if (val instanceof Number) return ((Number) val).longValue();
                try {
                    return Long.parseLong(val.toString().trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Integer getInt(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null) {
                if (val instanceof Number) return ((Number) val).intValue();
                try {
                    return Integer.parseInt(val.toString().trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private boolean getBool(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null) {
                if (val instanceof Boolean) return (Boolean) val;
                if (val instanceof Number) return ((Number) val).intValue() != 0;
                String s = val.toString().trim();
                return "true".equalsIgnoreCase(s) || "1".equals(s);
            }
        }
        return false;
    }

    private String getJsonOrStr(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null) {
                if (val instanceof Collection || val instanceof Map || val instanceof Object[]) {
                    try {
                        return objectMapper.writeValueAsString(val);
                    } catch (Exception ignored) {}
                }
                return val.toString();
            }
        }
        return null;
    }

    private String resolveCompanyId(String queryCompanyId, String headerCompanyId) {
        if (queryCompanyId != null && !queryCompanyId.isBlank()) {
            return queryCompanyId.trim();
        }
        if (headerCompanyId != null && !headerCompanyId.isBlank()) {
            return headerCompanyId.trim();
        }
        return null;
    }

    // ── Mappers to return both snake_case and camelCase ──
    private Map<String, Object> mapCategory(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = String.valueOf(row.get("id"));
        m.put("id", id);
        m.put("name", row.get("name"));
        m.put("description", row.get("description"));
        m.put("status", row.get("status"));
        m.put("created_at", row.get("created_at"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_by", row.get("created_by"));
        m.put("createdBy", row.get("created_by"));
        m.put("company_id", row.get("company_id"));
        m.put("companyId", row.get("company_id"));
        return m;
    }

    private Map<String, Object> mapSubcategory(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = String.valueOf(row.get("id"));
        m.put("id", id);
        m.put("name", row.get("name"));
        m.put("description", row.get("description"));
        m.put("category_id", row.get("category_id"));
        m.put("categoryId", row.get("category_id"));
        m.put("status", row.get("status"));
        m.put("created_at", row.get("created_at"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_by", row.get("created_by"));
        m.put("createdBy", row.get("created_by"));
        m.put("company_id", row.get("company_id"));
        m.put("companyId", row.get("company_id"));
        return m;
    }

    private Map<String, Object> mapServiceProvider(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = String.valueOf(row.get("id"));
        m.put("id", id);
        m.put("name", row.get("name"));
        m.put("description", row.get("description"));
        m.put("category_id", row.get("category_id"));
        m.put("categoryId", row.get("category_id"));
        m.put("subcategory_id", row.get("subcategory_id"));
        m.put("subcategoryId", row.get("subcategory_id"));
        m.put("sla", row.get("sla"));
        m.put("status", row.get("status"));
        m.put("created_at", row.get("created_at"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_by", row.get("created_by"));
        m.put("createdBy", row.get("created_by"));
        m.put("company_id", row.get("company_id"));
        m.put("companyId", row.get("company_id"));
        return m;
    }

    private Map<String, Object> mapGroupMember(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = String.valueOf(row.get("id"));
        m.put("id", id);
        m.put("user_id", row.get("user_id"));
        m.put("userId", row.get("user_id"));
        m.put("user_name", row.get("user_name"));
        m.put("userName", row.get("user_name"));
        m.put("user_email", row.get("user_email"));
        m.put("userEmail", row.get("user_email"));
        m.put("group_id", row.get("group_id"));
        m.put("groupId", row.get("group_id"));
        m.put("role_in_group", row.get("role_in_group"));
        m.put("roleInGroup", row.get("role_in_group"));
        m.put("is_primary", row.get("is_primary"));
        m.put("isPrimary", getBool(row, "is_primary"));
        m.put("availability_status", row.get("availability_status"));
        m.put("availabilityStatus", row.get("availability_status"));
        m.put("current_workload", row.get("current_workload"));
        m.put("currentWorkload", row.get("current_workload"));
        
        Object skillsVal = row.get("skills");
        if (skillsVal != null) {
            String skillsStr = skillsVal.toString();
            if (skillsStr.trim().startsWith("[")) {
                try {
                    m.put("skills", objectMapper.readValue(skillsStr, List.class));
                } catch (Exception e) {
                    m.put("skills", List.of(skillsStr.split(",")));
                }
            } else {
                m.put("skills", List.of(skillsStr.split(",")));
            }
        } else {
            m.put("skills", List.of());
        }
        
        m.put("status", row.get("status"));
        m.put("created_at", row.get("created_at"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_by", row.get("created_by"));
        m.put("createdBy", row.get("created_by"));
        m.put("company_id", row.get("company_id"));
        m.put("companyId", row.get("company_id"));
        return m;
    }

    private Map<String, Object> mapGroup(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = String.valueOf(row.get("id"));
        m.put("id", id);
        m.put("name", row.get("name"));
        m.put("description", row.get("description"));
        m.put("manager_uid", row.get("manager_uid"));
        m.put("managerUid", row.get("manager_uid"));
        m.put("managerId", row.get("manager_uid"));
        m.put("manager_name", row.get("manager_name"));
        m.put("managerName", row.get("manager_name"));
        m.put("assignment_email", row.get("assignment_email"));
        m.put("assignmentEmail", row.get("assignment_email"));
        m.put("emailAlias", row.get("assignment_email"));
        
        boolean isActive = getBool(row, "is_active");
        m.put("is_active", isActive);
        m.put("isActive", isActive);
        m.put("status", isActive ? "active" : "inactive");
        
        m.put("created_at", row.get("created_at"));
        m.put("createdAt", row.get("created_at"));
        m.put("updated_at", row.get("updated_at"));
        m.put("updatedAt", row.get("updated_at"));
        m.put("company_id", row.get("company_id"));
        m.put("companyId", row.get("company_id"));
        return m;
    }

    // ── GET Branding Settings ────────────────────────────────────────────────
    @GetMapping("/settings/branding")
    public ResponseEntity<?> getBranding() {
        log.info("[Branding] Fetching branding settings");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'branding'"
            );
            if (!rows.isEmpty()) {
                String settingVal = (String) rows.get(0).get("setting_value");
                if (settingVal != null && !settingVal.isBlank()) {
                    try {
                        Map<?, ?> parsed = objectMapper.readValue(settingVal, Map.class);
                        return ResponseEntity.ok(parsed);
                    } catch (Exception e) {
                        log.warn("[Branding] Failed to parse branding JSON, using fallback", e);
                    }
                }
            }
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("companyName", "Connect");
            fallback.put("logoBase64", null);
            fallback.put("logoType", null);
            return ResponseEntity.ok(fallback);
        } catch (Exception e) {
            log.error("[Branding] Error fetching branding settings: {}", e.getMessage(), e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("companyName", "Connect");
            fallback.put("logoBase64", null);
            fallback.put("logoType", null);
            return ResponseEntity.ok(fallback);
        }
    }

    // ── POST Branding Settings ───────────────────────────────────────────────
    @PostMapping("/settings/branding")
    @Transactional
    public ResponseEntity<?> postBranding(@RequestBody Map<String, Object> body) {
        log.info("[Branding] Updating branding settings with body: {}", body);
        try {
            String companyName = (String) body.getOrDefault("companyName", "Connect");
            String logoBase64 = (String) body.get("logoBase64");
            String logoType = (String) body.get("logoType");
            String updatedBy = (String) body.getOrDefault("updatedBy", "System");

            Map<String, Object> brandingMap = new HashMap<>();
            brandingMap.put("companyName", companyName);
            brandingMap.put("logoBase64", logoBase64);
            brandingMap.put("logoType", logoType);
            String brandingJson = objectMapper.writeValueAsString(brandingMap);

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM system_settings WHERE setting_key = 'branding'"
            );

            if (!existing.isEmpty()) {
                jdbcTemplate.update(
                    "UPDATE system_settings SET setting_value = ?, updated_by = ? WHERE setting_key = 'branding'",
                    brandingJson, updatedBy
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO system_settings (setting_key, setting_value, setting_type, description, updated_by) VALUES (?, ?, ?, ?, ?)",
                    "branding", brandingJson, "json", "Branding logo and company name", updatedBy
                );
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[Branding] Error saving branding: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── GET Typography Settings ──────────────────────────────────────────────
    @GetMapping("/settings/typography")
    public ResponseEntity<?> getTypography() {
        log.info("[Typography] Fetching typography settings");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'typography'"
            );
            if (!rows.isEmpty()) {
                String settingVal = (String) rows.get(0).get("setting_value");
                if (settingVal != null && !settingVal.isBlank()) {
                    try {
                        Map<?, ?> parsed = objectMapper.readValue(settingVal, Map.class);
                        return ResponseEntity.ok(parsed);
                    } catch (Exception e) {
                        log.warn("[Typography] Failed to parse typography JSON, using fallback", e);
                    }
                }
            }
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("globalFont", "Inter");
            fallback.put("loginFont", "Inter");
            fallback.put("dashboardFont", "Inter");
            fallback.put("ticketFont", "Inter");
            fallback.put("reportFont", "Inter");
            fallback.put("portalFont", "Inter");
            fallback.put("kbFont", "Inter");
            fallback.put("profileFont", "Inter");
            fallback.put("customFonts", new ArrayList<>());
            return ResponseEntity.ok(fallback);
        } catch (Exception e) {
            log.error("[Typography] Error fetching typography settings: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST Typography Settings ─────────────────────────────────────────────
    @PostMapping("/settings/typography")
    @Transactional
    public ResponseEntity<?> postTypography(@RequestBody Map<String, Object> body) {
        log.info("[Typography] Updating typography settings with body: {}", body);
        try {
            String typographyJson = objectMapper.writeValueAsString(body);
            String updatedBy = (String) body.getOrDefault("updatedBy", "System");

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM system_settings WHERE setting_key = 'typography'"
            );

            if (!existing.isEmpty()) {
                jdbcTemplate.update(
                    "UPDATE system_settings SET setting_value = ?, updated_by = ? WHERE setting_key = 'typography'",
                    typographyJson, updatedBy
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO system_settings (setting_key, setting_value, setting_type, description, updated_by) VALUES (?, ?, ?, ?, ?)",
                    "typography", typographyJson, "json", "Typography and custom font settings", updatedBy
                );
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[Typography] Error saving typography: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── POST Custom Font Upload ──────────────────────────────────────────────
    @PostMapping("/settings/upload-font")
    public ResponseEntity<?> uploadFont(@RequestParam("fontFile") org.springframework.web.multipart.MultipartFile file) {
        log.info("[Typography] Uploading custom font file: {}", file.getOriginalFilename());
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
            }
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank() || (!filename.endsWith(".woff") && !filename.endsWith(".woff2") && !filename.endsWith(".ttf") && !filename.endsWith(".otf"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported font file extension"));
            }
            
            java.io.File uploadDir = new java.io.File("./public/uploads").getAbsoluteFile();
            if (!uploadDir.exists()) uploadDir.mkdirs();
            java.io.File destination = new java.io.File(uploadDir, filename);
            
            java.nio.file.Files.copy(
                file.getInputStream(), 
                destination.toPath(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            
            return ResponseEntity.ok(Map.of("success", true, "font_url", "/uploads/" + filename, "font_name", filename.replaceAll("\\.[^.]+$", "")));
        } catch (Exception e) {
            log.error("[Typography] Error uploading font file: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── 1. SETTINGS CATEGORIES ────────────────────────────────────────────────
    @GetMapping("/settings_categories")
    public ResponseEntity<?> getCategories(
            @RequestParam(value = "company_id", required = false) String queryCompanyId,
            @RequestHeader(value = "x-company-id", required = false) String headerCompanyId) {
        String companyId = resolveCompanyId(queryCompanyId, headerCompanyId);
        log.info("[Categories] Fetching categories with companyId: {}", companyId);
        try {
            List<Map<String, Object>> rows;
            if (companyId != null) {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM settings_categories WHERE company_id = ? OR company_id IS NULL ORDER BY name ASC",
                    companyId
                );
            } else {
                rows = jdbcTemplate.queryForList("SELECT * FROM settings_categories ORDER BY name ASC");
            }
            return ResponseEntity.ok(rows.stream().map(this::mapCategory).toList());
        } catch (Exception e) {
            log.error("[Categories] Failed to fetch categories: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings_categories")
    @Transactional
    public ResponseEntity<?> createCategory(@RequestBody Map<String, Object> body) {
        log.info("[Categories] Creating category: {}", body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "cat_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String status = getStr(body, "status");
            if (status == null) status = "active";
            String createdBy = getStr(body, "created_by", "createdBy");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "INSERT INTO settings_categories (id, name, description, status, created_by, company_id) VALUES (?, ?, ?, ?, ?, ?)",
                id, name, description, status, createdBy, companyId
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_categories WHERE id = ?", id);
            return ResponseEntity.ok(mapCategory(rows.get(0)));
        } catch (Exception e) {
            log.error("[Categories] Failed to create category: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings_categories/{id}")
    @Transactional
    public ResponseEntity<?> updateCategory(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[Categories] Updating category {} with body: {}", id, body);
        try {
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String status = getStr(body, "status");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "UPDATE settings_categories SET name = ?, description = ?, status = ?, company_id = ? WHERE id = ?",
                name, description, status, companyId, id
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_categories WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(mapCategory(rows.get(0)));
        } catch (Exception e) {
            log.error("[Categories] Failed to update category: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_categories/{id}")
    @Transactional
    public ResponseEntity<?> deleteCategory(@PathVariable String id) {
        log.info("[Categories] Deleting category {}", id);
        try {
            jdbcTemplate.update("DELETE FROM settings_categories WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[Categories] Failed to delete category: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── 2. SETTINGS SUBCATEGORIES ─────────────────────────────────────────────
    @GetMapping("/settings_subcategories")
    public ResponseEntity<?> getSubcategories(
            @RequestParam(value = "company_id", required = false) String queryCompanyId,
            @RequestHeader(value = "x-company-id", required = false) String headerCompanyId) {
        String companyId = resolveCompanyId(queryCompanyId, headerCompanyId);
        log.info("[Subcategories] Fetching subcategories with companyId: {}", companyId);
        try {
            List<Map<String, Object>> rows;
            if (companyId != null) {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM settings_subcategories WHERE company_id = ? OR company_id IS NULL ORDER BY name ASC",
                    companyId
                );
            } else {
                rows = jdbcTemplate.queryForList("SELECT * FROM settings_subcategories ORDER BY name ASC");
            }
            return ResponseEntity.ok(rows.stream().map(this::mapSubcategory).toList());
        } catch (Exception e) {
            log.error("[Subcategories] Failed to fetch subcategories: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings_subcategories")
    @Transactional
    public ResponseEntity<?> createSubcategory(@RequestBody Map<String, Object> body) {
        log.info("[Subcategories] Creating subcategory: {}", body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "sub_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String categoryId = getStr(body, "category_id", "categoryId");
            String status = getStr(body, "status");
            if (status == null) status = "active";
            String createdBy = getStr(body, "created_by", "createdBy");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "INSERT INTO settings_subcategories (id, name, description, category_id, status, created_by, company_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, name, description, categoryId, status, createdBy, companyId
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_subcategories WHERE id = ?", id);
            return ResponseEntity.ok(mapSubcategory(rows.get(0)));
        } catch (Exception e) {
            log.error("[Subcategories] Failed to create subcategory: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings_subcategories/{id}")
    @Transactional
    public ResponseEntity<?> updateSubcategory(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[Subcategories] Updating subcategory {} with body: {}", id, body);
        try {
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String categoryId = getStr(body, "category_id", "categoryId");
            String status = getStr(body, "status");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "UPDATE settings_subcategories SET name = ?, description = ?, category_id = ?, status = ?, company_id = ? WHERE id = ?",
                name, description, categoryId, status, companyId, id
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_subcategories WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(mapSubcategory(rows.get(0)));
        } catch (Exception e) {
            log.error("[Subcategories] Failed to update subcategory: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_subcategories/{id}")
    @Transactional
    public ResponseEntity<?> deleteSubcategory(@PathVariable String id) {
        log.info("[Subcategories] Deleting subcategory {}", id);
        try {
            jdbcTemplate.update("DELETE FROM settings_subcategories WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[Subcategories] Failed to delete subcategory: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── 3. SETTINGS SERVICE PROVIDERS ─────────────────────────────────────────
    @GetMapping("/settings_service_providers")
    public ResponseEntity<?> getServiceProviders(
            @RequestParam(value = "company_id", required = false) String queryCompanyId,
            @RequestHeader(value = "x-company-id", required = false) String headerCompanyId) {
        String companyId = resolveCompanyId(queryCompanyId, headerCompanyId);
        log.info("[ServiceProviders] Fetching service providers with companyId: {}", companyId);
        try {
            List<Map<String, Object>> rows;
            if (companyId != null) {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM settings_service_providers WHERE company_id = ? OR company_id IS NULL ORDER BY name ASC",
                    companyId
                );
            } else {
                rows = jdbcTemplate.queryForList("SELECT * FROM settings_service_providers ORDER BY name ASC");
            }
            return ResponseEntity.ok(rows.stream().map(this::mapServiceProvider).toList());
        } catch (Exception e) {
            log.error("[ServiceProviders] Failed to fetch service providers: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings_service_providers")
    @Transactional
    public ResponseEntity<?> createServiceProvider(@RequestBody Map<String, Object> body) {
        log.info("[ServiceProviders] Creating service provider: {}", body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "prov_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String categoryId = getStr(body, "category_id", "categoryId");
            String subcategoryId = getStr(body, "subcategory_id", "subcategoryId");
            String sla = getStr(body, "sla");
            String status = getStr(body, "status");
            if (status == null) status = "active";
            String createdBy = getStr(body, "created_by", "createdBy");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "INSERT INTO settings_service_providers (id, name, description, category_id, subcategory_id, sla, status, created_by, company_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, description, categoryId, subcategoryId, sla, status, createdBy, companyId
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_service_providers WHERE id = ?", id);
            return ResponseEntity.ok(mapServiceProvider(rows.get(0)));
        } catch (Exception e) {
            log.error("[ServiceProviders] Failed to create service provider: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings_service_providers/{id}")
    @Transactional
    public ResponseEntity<?> updateServiceProvider(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[ServiceProviders] Updating service provider {} with body: {}", id, body);
        try {
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String categoryId = getStr(body, "category_id", "categoryId");
            String subcategoryId = getStr(body, "subcategory_id", "subcategoryId");
            String sla = getStr(body, "sla");
            String status = getStr(body, "status");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "UPDATE settings_service_providers SET name = ?, description = ?, category_id = ?, subcategory_id = ?, sla = ?, status = ?, company_id = ? WHERE id = ?",
                name, description, categoryId, subcategoryId, sla, status, companyId, id
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_service_providers WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(mapServiceProvider(rows.get(0)));
        } catch (Exception e) {
            log.error("[ServiceProviders] Failed to update service provider: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_service_providers/{id}")
    @Transactional
    public ResponseEntity<?> deleteServiceProvider(@PathVariable String id) {
        log.info("[ServiceProviders] Deleting service provider {}", id);
        try {
            jdbcTemplate.update("DELETE FROM settings_service_providers WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[ServiceProviders] Failed to delete service provider: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── 4. SETTINGS GROUP MEMBERS ─────────────────────────────────────────────
    @GetMapping("/settings_group_members")
    public ResponseEntity<?> getGroupMembers(
            @RequestParam(value = "company_id", required = false) String queryCompanyId,
            @RequestHeader(value = "x-company-id", required = false) String headerCompanyId) {
        String companyId = resolveCompanyId(queryCompanyId, headerCompanyId);
        log.info("[GroupMembers] Fetching group members with companyId: {}", companyId);
        try {
            List<Map<String, Object>> rows;
            if (companyId != null) {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM settings_group_members WHERE company_id = ? OR company_id IS NULL ORDER BY user_name ASC",
                    companyId
                );
            } else {
                rows = jdbcTemplate.queryForList("SELECT * FROM settings_group_members ORDER BY user_name ASC");
            }
            return ResponseEntity.ok(rows.stream().map(this::mapGroupMember).toList());
        } catch (Exception e) {
            log.error("[GroupMembers] Failed to fetch group members: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings_group_members")
    @Transactional
    public ResponseEntity<?> createGroupMember(@RequestBody Map<String, Object> body) {
        log.info("[GroupMembers] Creating group member: {}", body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "mem_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String userId = getStr(body, "user_id", "userId");
            String userName = getStr(body, "user_name", "userName");
            String userEmail = getStr(body, "user_email", "userEmail");
            String groupId = getStr(body, "group_id", "groupId");
            String roleInGroup = getStr(body, "role_in_group", "roleInGroup");
            boolean isPrimary = getBool(body, "is_primary", "isPrimary");
            String availabilityStatus = getStr(body, "availability_status", "availabilityStatus");
            if (availabilityStatus == null) availabilityStatus = "available";
            Integer currentWorkload = getInt(body, "current_workload", "currentWorkload");
            if (currentWorkload == null) currentWorkload = 0;
            String skills = getJsonOrStr(body, "skills");
            String status = getStr(body, "status");
            if (status == null) status = "active";
            String createdBy = getStr(body, "created_by", "createdBy");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "INSERT INTO settings_group_members (id, user_id, user_name, user_email, group_id, role_in_group, is_primary, availability_status, current_workload, skills, status, created_by, company_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, userName, userEmail, groupId, roleInGroup, isPrimary ? 1 : 0, availabilityStatus, currentWorkload, skills, status, createdBy, companyId
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_group_members WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupMember(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupMembers] Failed to create group member: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings_group_members/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupMember(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupMembers] Updating group member {} with body: {}", id, body);
        try {
            String userId = getStr(body, "user_id", "userId");
            String userName = getStr(body, "user_name", "userName");
            String userEmail = getStr(body, "user_email", "userEmail");
            String groupId = getStr(body, "group_id", "groupId");
            String roleInGroup = getStr(body, "role_in_group", "roleInGroup");
            boolean isPrimary = getBool(body, "is_primary", "isPrimary");
            String availabilityStatus = getStr(body, "availability_status", "availabilityStatus");
            Integer currentWorkload = getInt(body, "current_workload", "currentWorkload");
            String skills = getJsonOrStr(body, "skills");
            String status = getStr(body, "status");
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "UPDATE settings_group_members SET user_id = ?, user_name = ?, user_email = ?, group_id = ?, role_in_group = ?, is_primary = ?, availability_status = ?, current_workload = ?, skills = ?, status = ?, company_id = ? WHERE id = ?",
                userId, userName, userEmail, groupId, roleInGroup, isPrimary ? 1 : 0, availabilityStatus, currentWorkload, skills, status, companyId, id
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_group_members WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(mapGroupMember(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupMembers] Failed to update group member: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_group_members/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupMember(@PathVariable String id) {
        log.info("[GroupMembers] Deleting group member {}", id);
        try {
            jdbcTemplate.update("DELETE FROM settings_group_members WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupMembers] Failed to delete group member: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── 5. SETTINGS GROUPS ────────────────────────────────────────────────────
    @GetMapping("/settings_groups")
    public ResponseEntity<?> getGroups(
            @RequestParam(value = "company_id", required = false) String queryCompanyId,
            @RequestHeader(value = "x-company-id", required = false) String headerCompanyId) {
        String companyId = resolveCompanyId(queryCompanyId, headerCompanyId);
        log.info("[Groups] Fetching groups with companyId: {}", companyId);
        try {
            List<Map<String, Object>> rows;
            if (companyId != null) {
                rows = jdbcTemplate.queryForList(
                    "SELECT * FROM settings_groups WHERE company_id = ? OR company_id IS NULL ORDER BY name ASC",
                    companyId
                );
            } else {
                rows = jdbcTemplate.queryForList("SELECT * FROM settings_groups ORDER BY name ASC");
            }
            return ResponseEntity.ok(rows.stream().map(this::mapGroup).toList());
        } catch (Exception e) {
            log.error("[Groups] Failed to fetch groups: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings_groups")
    @Transactional
    public ResponseEntity<?> createGroup(@RequestBody Map<String, Object> body) {
        log.info("[Groups] Creating group: {}", body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "sg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String managerUid = getStr(body, "manager_uid", "managerUid", "managerId");
            String managerName = getStr(body, "manager_name", "managerName");
            String assignmentEmail = getStr(body, "assignment_email", "assignmentEmail", "emailAlias");
            boolean isActive = getBool(body, "is_active", "isActive");
            String status = getStr(body, "status");
            if (status != null) {
                isActive = "active".equalsIgnoreCase(status);
            }
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "INSERT INTO settings_groups (id, name, description, manager_uid, manager_name, assignment_email, is_active, company_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, description, managerUid, managerName, assignmentEmail, isActive ? 1 : 0, companyId
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_groups WHERE id = ?", id);
            return ResponseEntity.ok(mapGroup(rows.get(0)));
        } catch (Exception e) {
            log.error("[Groups] Failed to create group: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings_groups/{id}")
    @Transactional
    public ResponseEntity<?> updateGroup(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[Groups] Updating group {} with body: {}", id, body);
        try {
            String name = getStr(body, "name");
            String description = getStr(body, "description");
            String managerUid = getStr(body, "manager_uid", "managerUid", "managerId");
            String managerName = getStr(body, "manager_name", "managerName");
            String assignmentEmail = getStr(body, "assignment_email", "assignmentEmail", "emailAlias");
            boolean isActive = getBool(body, "is_active", "isActive");
            String status = getStr(body, "status");
            if (status != null) {
                isActive = "active".equalsIgnoreCase(status);
            }
            Long companyId = getLong(body, "company_id", "companyId");

            jdbcTemplate.update(
                "UPDATE settings_groups SET name = ?, description = ?, manager_uid = ?, manager_name = ?, assignment_email = ?, is_active = ?, company_id = ? WHERE id = ?",
                name, description, managerUid, managerName, assignmentEmail, isActive ? 1 : 0, companyId, id
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM settings_groups WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(mapGroup(rows.get(0)));
        } catch (Exception e) {
            log.error("[Groups] Failed to update group: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings_groups/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroup(@PathVariable String id) {
        log.info("[Groups] Deleting group {}", id);
        try {
            jdbcTemplate.update("DELETE FROM settings_groups WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[Groups] Failed to delete group: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── Helper method for double values ──
    private Double getDouble(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null) {
                if (val instanceof Number) return ((Number) val).doubleValue();
                try {
                    return Double.parseDouble(val.toString().trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ── 6. GROUPS KANBAN TASKS ────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/tasks")
    public ResponseEntity<?> getGroupTasks(@PathVariable String groupId) {
        log.info("[GroupTasks] Fetching tasks for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_tasks WHERE group_id = ? ORDER BY created_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupTask).toList());
        } catch (Exception e) {
            log.error("[GroupTasks] Failed to fetch tasks: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/tasks")
    @Transactional
    public ResponseEntity<?> createGroupTask(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupTasks] Creating task for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "gt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String title = getStr(body, "title");
            String description = getStr(body, "description");
            String assigneeId = getStr(body, "assigneeId", "assignee_id");
            String assigneeName = getStr(body, "assigneeName", "assignee_name");
            String priority = getStr(body, "priority");
            if (priority == null) priority = "Medium";
            String status = getStr(body, "status");
            if (status == null) status = "To Do";
            Integer storyPoints = getInt(body, "storyPoints", "story_points");
            if (storyPoints == null) storyPoints = 0;
            Double estimatedHours = getDouble(body, "estimatedHours", "estimated_hours");
            if (estimatedHours == null) estimatedHours = 0.0;
            Double actualHours = getDouble(body, "actualHours", "actual_hours");
            if (actualHours == null) actualHours = 0.0;
            String dueDate = getStr(body, "dueDate", "due_date");

            jdbcTemplate.update(
                "INSERT INTO groups_tasks (id, group_id, title, description, assignee_id, assignee_name, priority, status, story_points, estimated_hours, actual_hours, due_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupId, title, description, assigneeId, assigneeName, priority, status, storyPoints, estimatedHours, actualHours, dueDate
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_tasks WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupTask(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupTasks] Failed to create task: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/tasks/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupTask(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupTasks] Updating task {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_tasks WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String title = getStr(body, "title");
            if (title == null) title = (String) existing.get("title");
            String description = getStr(body, "description");
            if (description == null) description = (String) existing.get("description");
            String assigneeId = getStr(body, "assigneeId", "assignee_id");
            if (assigneeId == null) assigneeId = (String) existing.get("assignee_id");
            String assigneeName = getStr(body, "assigneeName", "assignee_name");
            if (assigneeName == null) assigneeName = (String) existing.get("assignee_name");
            String priority = getStr(body, "priority");
            if (priority == null) priority = (String) existing.get("priority");
            String status = getStr(body, "status");
            if (status == null) status = (String) existing.get("status");
            Integer storyPoints = getInt(body, "storyPoints", "story_points");
            if (storyPoints == null) storyPoints = (Integer) existing.get("story_points");
            Double estimatedHours = getDouble(body, "estimatedHours", "estimated_hours");
            if (estimatedHours == null) {
                Object val = existing.get("estimated_hours");
                estimatedHours = val != null ? ((Number) val).doubleValue() : 0.0;
            }
            Double actualHours = getDouble(body, "actualHours", "actual_hours");
            if (actualHours == null) {
                Object val = existing.get("actual_hours");
                actualHours = val != null ? ((Number) val).doubleValue() : 0.0;
            }
            String dueDate = getStr(body, "dueDate", "due_date");
            if (dueDate == null && existing.get("due_date") != null) dueDate = String.valueOf(existing.get("due_date"));

            jdbcTemplate.update(
                "UPDATE groups_tasks SET title = ?, description = ?, assignee_id = ?, assignee_name = ?, priority = ?, status = ?, story_points = ?, estimated_hours = ?, actual_hours = ?, due_date = ? WHERE id = ?",
                title, description, assigneeId, assigneeName, priority, status, storyPoints, estimatedHours, actualHours, dueDate, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_tasks WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupTask(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupTasks] Failed to update task: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/tasks/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupTask(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupTasks] Deleting task {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_tasks WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupTasks] Failed to delete task: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupTask(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("title", row.get("title"));
        m.put("description", row.get("description"));
        m.put("assigneeId", row.get("assignee_id"));
        m.put("assignee_id", row.get("assignee_id"));
        m.put("assigneeName", row.get("assignee_name"));
        m.put("assignee_name", row.get("assignee_name"));
        m.put("priority", row.get("priority"));
        m.put("status", row.get("status"));
        m.put("storyPoints", row.get("story_points"));
        m.put("story_points", row.get("story_points"));
        m.put("estimatedHours", row.get("estimated_hours"));
        m.put("estimated_hours", row.get("estimated_hours"));
        m.put("actualHours", row.get("actual_hours"));
        m.put("actual_hours", row.get("actual_hours"));
        m.put("dueDate", row.get("due_date"));
        m.put("due_date", row.get("due_date"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        m.put("updatedAt", row.get("updated_at"));
        m.put("updated_at", row.get("updated_at"));
        return m;
    }

    // ── 7. GROUPS EVENTS ──────────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/events")
    public ResponseEntity<?> getGroupEvents(@PathVariable String groupId) {
        log.info("[GroupEvents] Fetching events for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_events WHERE group_id = ? ORDER BY start_date ASC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupEvent).toList());
        } catch (Exception e) {
            log.error("[GroupEvents] Failed to fetch events: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/events")
    @Transactional
    public ResponseEntity<?> createGroupEvent(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupEvents] Creating event for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "evt_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String title = getStr(body, "title");
            String description = getStr(body, "description");
            String type = getStr(body, "type");
            if (type == null) type = "Meeting";
            String startDate = getStr(body, "startDate", "start_date");
            String endDate = getStr(body, "endDate", "end_date");
            Double estimatedHours = getDouble(body, "estimatedHours", "estimated_hours");
            if (estimatedHours == null) estimatedHours = 0.0;
            String priority = getStr(body, "priority");
            String assigneeId = getStr(body, "assigneeId", "assignee_id");
            String status = getStr(body, "status");
            if (status == null) status = "Planned";
            String dependencies = getStr(body, "dependencies");

            jdbcTemplate.update(
                "INSERT INTO groups_events (id, group_id, title, description, type, start_date, end_date, estimated_hours, priority, assignee_id, status, dependencies) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupId, title, description, type, startDate, endDate, estimatedHours, priority, assigneeId, status, dependencies
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_events WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupEvent(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupEvents] Failed to create event: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/events/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupEvent(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupEvents] Updating event {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_events WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String title = getStr(body, "title");
            if (title == null) title = (String) existing.get("title");
            String description = getStr(body, "description");
            if (description == null) description = (String) existing.get("description");
            String type = getStr(body, "type");
            if (type == null) type = (String) existing.get("type");
            String startDate = getStr(body, "startDate", "start_date");
            if (startDate == null && existing.get("start_date") != null) startDate = String.valueOf(existing.get("start_date"));
            String endDate = getStr(body, "endDate", "end_date");
            if (endDate == null && existing.get("end_date") != null) endDate = String.valueOf(existing.get("end_date"));
            Double estimatedHours = getDouble(body, "estimatedHours", "estimated_hours");
            if (estimatedHours == null) {
                Object val = existing.get("estimated_hours");
                estimatedHours = val != null ? ((Number) val).doubleValue() : 0.0;
            }
            String priority = getStr(body, "priority");
            if (priority == null) priority = (String) existing.get("priority");
            String assigneeId = getStr(body, "assigneeId", "assignee_id");
            if (assigneeId == null) assigneeId = (String) existing.get("assignee_id");
            String status = getStr(body, "status");
            if (status == null) status = (String) existing.get("status");
            String dependencies = getStr(body, "dependencies");
            if (dependencies == null) dependencies = (String) existing.get("dependencies");

            jdbcTemplate.update(
                "UPDATE groups_events SET title = ?, description = ?, type = ?, start_date = ?, end_date = ?, estimated_hours = ?, priority = ?, assignee_id = ?, status = ?, dependencies = ? WHERE id = ?",
                title, description, type, startDate, endDate, estimatedHours, priority, assigneeId, status, dependencies, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_events WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupEvent(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupEvents] Failed to update event: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/events/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupEvent(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupEvents] Deleting event {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_events WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupEvents] Failed to delete event: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupEvent(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("title", row.get("title"));
        m.put("description", row.get("description"));
        m.put("type", row.get("type"));
        m.put("startDate", row.get("start_date"));
        m.put("start_date", row.get("start_date"));
        m.put("endDate", row.get("end_date"));
        m.put("end_date", row.get("end_date"));
        m.put("estimatedHours", row.get("estimated_hours"));
        m.put("estimated_hours", row.get("estimated_hours"));
        m.put("priority", row.get("priority"));
        m.put("assigneeId", row.get("assignee_id"));
        m.put("assignee_id", row.get("assignee_id"));
        m.put("status", row.get("status"));
        m.put("dependencies", row.get("dependencies"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        return m;
    }

    // ── 8. GROUPS PLANS ───────────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/plans")
    public ResponseEntity<?> getGroupPlans(@PathVariable String groupId) {
        log.info("[GroupPlans] Fetching plans for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_plans WHERE group_id = ? ORDER BY created_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupPlan).toList());
        } catch (Exception e) {
            log.error("[GroupPlans] Failed to fetch plans: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/plans")
    @Transactional
    public ResponseEntity<?> createGroupPlan(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupPlans] Creating plan for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "pln_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String type = getStr(body, "type");
            String objective = getStr(body, "objective");
            Double plannedWork = getDouble(body, "plannedWork", "planned_work");
            if (plannedWork == null) plannedWork = 0.0;
            Double actualWork = getDouble(body, "actualWork", "actual_work");
            if (actualWork == null) actualWork = 0.0;
            Double completionRate = getDouble(body, "completionRate", "completion_rate");
            if (completionRate == null) completionRate = 0.0;
            Double delayRate = getDouble(body, "delayRate", "delay_rate");
            if (delayRate == null) delayRate = 0.0;

            jdbcTemplate.update(
                "INSERT INTO groups_plans (id, group_id, type, objective, planned_work, actual_work, completion_rate, delay_rate) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupId, type, objective, plannedWork, actualWork, completionRate, delayRate
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_plans WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupPlan(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupPlans] Failed to create plan: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/plans/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupPlan(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupPlans] Updating plan {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_plans WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String type = getStr(body, "type");
            if (type == null) type = (String) existing.get("type");
            String objective = getStr(body, "objective");
            if (objective == null) objective = (String) existing.get("objective");
            Double plannedWork = getDouble(body, "plannedWork", "planned_work");
            if (plannedWork == null) {
                Object val = existing.get("planned_work");
                plannedWork = val != null ? ((Number) val).doubleValue() : 0.0;
            }
            Double actualWork = getDouble(body, "actualWork", "actual_work");
            if (actualWork == null) {
                Object val = existing.get("actual_work");
                actualWork = val != null ? ((Number) val).doubleValue() : 0.0;
            }
            Double completionRate = getDouble(body, "completionRate", "completion_rate");
            if (completionRate == null) {
                Object val = existing.get("completion_rate");
                completionRate = val != null ? ((Number) val).doubleValue() : 0.0;
            }
            Double delayRate = getDouble(body, "delayRate", "delay_rate");
            if (delayRate == null) {
                Object val = existing.get("delay_rate");
                delayRate = val != null ? ((Number) val).doubleValue() : 0.0;
            }

            jdbcTemplate.update(
                "UPDATE groups_plans SET type = ?, objective = ?, planned_work = ?, actual_work = ?, completion_rate = ?, delay_rate = ? WHERE id = ?",
                type, objective, plannedWork, actualWork, completionRate, delayRate, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_plans WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupPlan(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupPlans] Failed to update plan: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/plans/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupPlan(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupPlans] Deleting plan {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_plans WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupPlans] Failed to delete plan: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupPlan(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("type", row.get("type"));
        m.put("objective", row.get("objective"));
        m.put("plannedWork", row.get("planned_work"));
        m.put("planned_work", row.get("planned_work"));
        m.put("actualWork", row.get("actual_work"));
        m.put("actual_work", row.get("actual_work"));
        m.put("completionRate", row.get("completion_rate"));
        m.put("completion_rate", row.get("completion_rate"));
        m.put("delayRate", row.get("delay_rate"));
        m.put("delay_rate", row.get("delay_rate"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        return m;
    }

    // ── 9. GROUPS STANDUPS ────────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/standups")
    public ResponseEntity<?> getGroupStandups(@PathVariable String groupId) {
        log.info("[GroupStandups] Fetching standups for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_standups WHERE group_id = ? ORDER BY standup_date DESC, created_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupStandup).toList());
        } catch (Exception e) {
            log.error("[GroupStandups] Failed to fetch standups: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/standups")
    @Transactional
    public ResponseEntity<?> createGroupStandup(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupStandups] Creating standup for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "std_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String userId = getStr(body, "userId", "user_id");
            String userName = getStr(body, "userName", "user_name");
            String yesterday = getStr(body, "yesterday");
            String today = getStr(body, "today");
            String blockers = getStr(body, "blockers");
            String date = getStr(body, "date", "standup_date");
            if (date == null) {
                date = LocalDate.now().toString();
            }

            jdbcTemplate.update(
                "INSERT INTO groups_standups (id, group_id, user_id, user_name, yesterday, today, blockers, standup_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupId, userId, userName, yesterday, today, blockers, date
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_standups WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupStandup(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupStandups] Failed to create standup: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/standups/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupStandup(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupStandups] Updating standup {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_standups WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String yesterday = getStr(body, "yesterday");
            if (yesterday == null) yesterday = (String) existing.get("yesterday");
            String today = getStr(body, "today");
            if (today == null) today = (String) existing.get("today");
            String blockers = getStr(body, "blockers");
            if (blockers == null) blockers = (String) existing.get("blockers");
            String date = getStr(body, "date", "standup_date");
            if (date == null && existing.get("standup_date") != null) date = String.valueOf(existing.get("standup_date"));

            jdbcTemplate.update(
                "UPDATE groups_standups SET yesterday = ?, today = ?, blockers = ?, standup_date = ? WHERE id = ?",
                yesterday, today, blockers, date, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_standups WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupStandup(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupStandups] Failed to update standup: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/standups/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupStandup(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupStandups] Deleting standup {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_standups WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupStandups] Failed to delete standup: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupStandup(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("userId", row.get("user_id"));
        m.put("user_id", row.get("user_id"));
        m.put("userName", row.get("user_name"));
        m.put("user_name", row.get("user_name"));
        m.put("yesterday", row.get("yesterday"));
        m.put("today", row.get("today"));
        m.put("blockers", row.get("blockers"));
        m.put("date", row.get("standup_date"));
        m.put("standup_date", row.get("standup_date"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        return m;
    }

    // ── 10. GROUPS RATINGS ────────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/ratings")
    public ResponseEntity<?> getGroupRatings(@PathVariable String groupId) {
        log.info("[GroupRatings] Fetching ratings for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_ratings WHERE group_id = ? ORDER BY rating_date DESC, created_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupRating).toList());
        } catch (Exception e) {
            log.error("[GroupRatings] Failed to fetch ratings: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/ratings")
    @Transactional
    public ResponseEntity<?> createGroupRating(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupRatings] Creating rating for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "rtg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String userId = getStr(body, "userId", "user_id");
            String userName = getStr(body, "userName", "user_name");
            Integer productivity = getInt(body, "productivity");
            if (productivity == null) productivity = 5;
            Integer quality = getInt(body, "quality");
            if (quality == null) quality = 5;
            Integer attendance = getInt(body, "attendance");
            if (attendance == null) attendance = 5;
            Integer communication = getInt(body, "communication");
            if (communication == null) communication = 5;
            Integer collaboration = getInt(body, "collaboration");
            if (collaboration == null) collaboration = 5;
            Integer ownership = getInt(body, "ownership");
            if (ownership == null) ownership = 5;
            Double score = getDouble(body, "score");
            if (score == null) {
                score = (productivity + quality + attendance + communication + collaboration + ownership) / 6.0;
                score = Math.round(score * 10.0) / 10.0;
            }
            String frequency = getStr(body, "frequency");
            if (frequency == null) frequency = "Weekly";
            String date = getStr(body, "date", "rating_date");
            if (date == null) {
                date = LocalDate.now().toString();
            }
            String ratedBy = getStr(body, "ratedBy", "rated_by");

            jdbcTemplate.update(
                "INSERT INTO groups_ratings (id, group_id, user_id, user_name, productivity, quality, attendance, communication, collaboration, ownership, score, frequency, rating_date, rated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupId, userId, userName, productivity, quality, attendance, communication, collaboration, ownership, score, frequency, date, ratedBy
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_ratings WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupRating(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupRatings] Failed to create rating: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/ratings/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupRating(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupRatings] Updating rating {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_ratings WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            Integer productivity = getInt(body, "productivity");
            if (productivity == null) productivity = (Integer) existing.get("productivity");
            Integer quality = getInt(body, "quality");
            if (quality == null) quality = (Integer) existing.get("quality");
            Integer attendance = getInt(body, "attendance");
            if (attendance == null) attendance = (Integer) existing.get("attendance");
            Integer communication = getInt(body, "communication");
            if (communication == null) communication = (Integer) existing.get("communication");
            Integer collaboration = getInt(body, "collaboration");
            if (collaboration == null) collaboration = (Integer) existing.get("collaboration");
            Integer ownership = getInt(body, "ownership");
            if (ownership == null) ownership = (Integer) existing.get("ownership");
            Double score = getDouble(body, "score");
            if (score == null) {
                score = (productivity + quality + attendance + communication + collaboration + ownership) / 6.0;
                score = Math.round(score * 10.0) / 10.0;
            }
            String frequency = getStr(body, "frequency");
            if (frequency == null) frequency = (String) existing.get("frequency");
            String date = getStr(body, "date", "rating_date");
            if (date == null && existing.get("rating_date") != null) date = String.valueOf(existing.get("rating_date"));
            String ratedBy = getStr(body, "ratedBy", "rated_by");
            if (ratedBy == null) ratedBy = (String) existing.get("rated_by");

            jdbcTemplate.update(
                "UPDATE groups_ratings SET productivity = ?, quality = ?, attendance = ?, communication = ?, collaboration = ?, ownership = ?, score = ?, frequency = ?, rating_date = ?, rated_by = ? WHERE id = ?",
                productivity, quality, attendance, communication, collaboration, ownership, score, frequency, date, ratedBy, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_ratings WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupRating(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupRatings] Failed to update rating: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/ratings/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupRating(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupRatings] Deleting rating {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_ratings WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupRatings] Failed to delete rating: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupRating(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("userId", row.get("user_id"));
        m.put("user_id", row.get("user_id"));
        m.put("userName", row.get("user_name"));
        m.put("user_name", row.get("user_name"));
        m.put("productivity", row.get("productivity"));
        m.put("quality", row.get("quality"));
        m.put("attendance", row.get("attendance"));
        m.put("communication", row.get("communication"));
        m.put("collaboration", row.get("collaboration"));
        m.put("ownership", row.get("ownership"));
        m.put("score", row.get("score"));
        m.put("frequency", row.get("frequency"));
        m.put("date", row.get("rating_date"));
        m.put("rating_date", row.get("rating_date"));
        m.put("ratedBy", row.get("rated_by"));
        m.put("rated_by", row.get("rated_by"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        return m;
    }

    // ── 11. GROUPS DISCUSSIONS ────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/discussions")
    public ResponseEntity<?> getGroupDiscussions(@PathVariable String groupId) {
        log.info("[GroupDiscussions] Fetching discussions for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_discussions WHERE group_id = ? ORDER BY created_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupDiscussion).toList());
        } catch (Exception e) {
            log.error("[GroupDiscussions] Failed to fetch discussions: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/discussions")
    @Transactional
    public ResponseEntity<?> createGroupDiscussion(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupDiscussions] Creating discussion for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "dsc_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String type = getStr(body, "type");
            if (type == null) type = "discussion";
            String title = getStr(body, "title");
            String content = getStr(body, "content");
            String authorName = getStr(body, "authorName", "author_name");

            jdbcTemplate.update(
                "INSERT INTO groups_discussions (id, group_id, type, title, content, author_name) VALUES (?, ?, ?, ?, ?, ?)",
                id, groupId, type, title, content, authorName
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_discussions WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupDiscussion(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupDiscussions] Failed to create discussion: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/discussions/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupDiscussion(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupDiscussions] Updating discussion {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_discussions WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String type = getStr(body, "type");
            if (type == null) type = (String) existing.get("type");
            String title = getStr(body, "title");
            if (title == null) title = (String) existing.get("title");
            String content = getStr(body, "content");
            if (content == null) content = (String) existing.get("content");
            String authorName = getStr(body, "authorName", "author_name");
            if (authorName == null) authorName = (String) existing.get("author_name");

            jdbcTemplate.update(
                "UPDATE groups_discussions SET type = ?, title = ?, content = ?, author_name = ? WHERE id = ?",
                type, title, content, authorName, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_discussions WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupDiscussion(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupDiscussions] Failed to update discussion: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/discussions/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupDiscussion(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupDiscussions] Deleting discussion {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_discussions WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupDiscussions] Failed to delete discussion: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupDiscussion(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("type", row.get("type"));
        m.put("title", row.get("title"));
        m.put("content", row.get("content"));
        m.put("authorName", row.get("author_name"));
        m.put("author_name", row.get("author_name"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        return m;
    }

    // ── 12. GROUPS KNOWLEDGE BASE ─────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/kb")
    public ResponseEntity<?> getGroupKB(@PathVariable String groupId) {
        log.info("[GroupKB] Fetching articles for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_kb WHERE group_id = ? ORDER BY updated_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupKB).toList());
        } catch (Exception e) {
            log.error("[GroupKB] Failed to fetch articles: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/kb")
    @Transactional
    public ResponseEntity<?> createGroupKB(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupKB] Creating article for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "kb_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String title = getStr(body, "title");
            String content = getStr(body, "content");
            String category = getStr(body, "category");
            String authorName = getStr(body, "authorName", "author_name");

            jdbcTemplate.update(
                "INSERT INTO groups_kb (id, group_id, title, content, category, author_name) VALUES (?, ?, ?, ?, ?, ?)",
                id, groupId, title, content, category, authorName
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_kb WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupKB(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupKB] Failed to create article: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/kb/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupKB(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupKB] Updating article {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_kb WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String title = getStr(body, "title");
            if (title == null) title = (String) existing.get("title");
            String content = getStr(body, "content");
            if (content == null) content = (String) existing.get("content");
            String category = getStr(body, "category");
            if (category == null) category = (String) existing.get("category");
            String authorName = getStr(body, "authorName", "author_name");
            if (authorName == null) authorName = (String) existing.get("author_name");

            jdbcTemplate.update(
                "UPDATE groups_kb SET title = ?, content = ?, category = ?, author_name = ? WHERE id = ?",
                title, content, category, authorName, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_kb WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupKB(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupKB] Failed to update article: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/kb/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupKB(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupKB] Deleting article {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_kb WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupKB] Failed to delete article: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupKB(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("title", row.get("title"));
        m.put("content", row.get("content"));
        m.put("category", row.get("category"));
        m.put("authorName", row.get("author_name"));
        m.put("author_name", row.get("author_name"));
        m.put("updatedAt", row.get("updated_at"));
        m.put("updated_at", row.get("updated_at"));
        return m;
    }

    // ── 13. GROUPS ESCALATIONS ────────────────────────────────────────────────
    @GetMapping("/settings/groups/{groupId}/escalations")
    public ResponseEntity<?> getGroupEscalations(@PathVariable String groupId) {
        log.info("[GroupEscalations] Fetching escalations for groupId: {}", groupId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM groups_escalations WHERE group_id = ? ORDER BY created_at DESC", groupId
            );
            return ResponseEntity.ok(rows.stream().map(this::mapGroupEscalation).toList());
        } catch (Exception e) {
            log.error("[GroupEscalations] Failed to fetch escalations: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/settings/groups/{groupId}/escalations")
    @Transactional
    public ResponseEntity<?> createGroupEscalation(@PathVariable String groupId, @RequestBody Map<String, Object> body) {
        log.info("[GroupEscalations] Creating escalation for groupId: {}, body: {}", groupId, body);
        try {
            String id = getStr(body, "id");
            if (id == null) {
                id = "esc_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            String title = getStr(body, "title");
            String description = getStr(body, "description");
            String status = getStr(body, "status");
            String priority = getStr(body, "priority");
            String assigneeName = getStr(body, "assigneeName", "assignee_name");

            jdbcTemplate.update(
                "INSERT INTO groups_escalations (id, group_id, title, description, status, priority, assignee_name) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, groupId, title, description, status, priority, assigneeName
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_escalations WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupEscalation(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupEscalations] Failed to create escalation: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings/groups/{groupId}/escalations/{id}")
    @Transactional
    public ResponseEntity<?> updateGroupEscalation(@PathVariable String groupId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GroupEscalations] Updating escalation {} for groupId: {}, body: {}", id, groupId, body);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM groups_escalations WHERE id = ?", id);
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            Map<String, Object> existing = rows.get(0);

            String title = getStr(body, "title");
            if (title == null) title = (String) existing.get("title");
            String description = getStr(body, "description");
            if (description == null) description = (String) existing.get("description");
            String status = getStr(body, "status");
            if (status == null) status = (String) existing.get("status");
            String priority = getStr(body, "priority");
            if (priority == null) priority = (String) existing.get("priority");
            String assigneeName = getStr(body, "assigneeName", "assignee_name");
            if (assigneeName == null) assigneeName = (String) existing.get("assignee_name");

            jdbcTemplate.update(
                "UPDATE groups_escalations SET title = ?, description = ?, status = ?, priority = ?, assignee_name = ? WHERE id = ?",
                title, description, status, priority, assigneeName, id
            );

            rows = jdbcTemplate.queryForList("SELECT * FROM groups_escalations WHERE id = ?", id);
            return ResponseEntity.ok(mapGroupEscalation(rows.get(0)));
        } catch (Exception e) {
            log.error("[GroupEscalations] Failed to update escalation: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/groups/{groupId}/escalations/{id}")
    @Transactional
    public ResponseEntity<?> deleteGroupEscalation(@PathVariable String groupId, @PathVariable String id) {
        log.info("[GroupEscalations] Deleting escalation {} for groupId: {}", id, groupId);
        try {
            jdbcTemplate.update("DELETE FROM groups_escalations WHERE id = ?", id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GroupEscalations] Failed to delete escalation: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> mapGroupEscalation(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(row.get("id")));
        m.put("groupId", row.get("group_id"));
        m.put("group_id", row.get("group_id"));
        m.put("title", row.get("title"));
        m.put("description", row.get("description"));
        m.put("status", row.get("status"));
        m.put("priority", row.get("priority"));
        m.put("assigneeName", row.get("assignee_name"));
        m.put("assignee_name", row.get("assignee_name"));
        m.put("createdAt", row.get("created_at"));
        m.put("created_at", row.get("created_at"));
        return m;
    }

    // ── 14. GLOBAL SYSTEM SETTINGS ─────────────────────────────────────────────
    @GetMapping("/settings_global/{id}")
    public ResponseEntity<?> getGlobalSetting(@PathVariable String id) {
        log.info("[GlobalSettings] Fetching settings for key: {}", id);
        try {
            String dbKey = "settings_global_" + id;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT setting_value FROM system_settings WHERE setting_key = ?", dbKey
            );
            if (!rows.isEmpty()) {
                String settingVal = (String) rows.get(0).get("setting_value");
                if (settingVal != null && !settingVal.isBlank()) {
                    try {
                        Map<?, ?> parsed = objectMapper.readValue(settingVal, Map.class);
                        return ResponseEntity.ok(parsed);
                    } catch (Exception e) {
                        log.warn("[GlobalSettings] Failed to parse setting JSON for key {}", dbKey, e);
                    }
                }
            }
            return ResponseEntity.ok(Map.of());
        } catch (Exception e) {
            log.error("[GlobalSettings] Error fetching settings for key {}: {}", id, e.getMessage(), e);
            return ResponseEntity.ok(Map.of());
        }
    }

    @PutMapping("/settings_global/{id}")
    @Transactional
    public ResponseEntity<?> updateGlobalSetting(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GlobalSettings] Updating settings for key: {} with body: {}", id, body);
        return saveGlobalSetting(id, body);
    }

    @PostMapping("/settings_global/{id}")
    @Transactional
    public ResponseEntity<?> createGlobalSetting(@PathVariable String id, @RequestBody Map<String, Object> body) {
        log.info("[GlobalSettings] Creating settings for key: {} with body: {}", id, body);
        return saveGlobalSetting(id, body);
    }

    private ResponseEntity<?> saveGlobalSetting(String id, Map<String, Object> body) {
        try {
            String dbKey = "settings_global_" + id;
            String jsonVal = objectMapper.writeValueAsString(body);
            String updatedBy = getStr(body, "updatedBy", "updated_by");
            if (updatedBy == null) updatedBy = "System";

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM system_settings WHERE setting_key = ?", dbKey
            );

            if (!existing.isEmpty()) {
                jdbcTemplate.update(
                    "UPDATE system_settings SET setting_value = ?, updated_by = ? WHERE setting_key = ?",
                    jsonVal, updatedBy, dbKey
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO system_settings (setting_key, setting_value, setting_type, description, updated_by) VALUES (?, ?, ?, ?, ?)",
                    dbKey, jsonVal, "json", "Global system settings for " + id, updatedBy
                );
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[GlobalSettings] Error saving settings for key {}: {}", id, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
