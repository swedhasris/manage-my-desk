package com.connectit.core.controller;

import com.connectit.core.model.User;
import com.connectit.core.service.UserService;
import com.connectit.core.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.sql.PreparedStatement;
import java.sql.Statement;
import com.connectit.core.util.DbUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/users")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(userService.findAll().stream().map(this::serialize).toList());
    }

    @GetMapping("/users/{uid}")
    public ResponseEntity<?> get(@PathVariable String uid) {
        return userService.findByUid(uid)
            .map(u -> ResponseEntity.ok((Object) serialize(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
        try {
            String restrictedModulesStr = "";
            if (body.get("restrictedModules") != null) {
                Object val = body.get("restrictedModules");
                if (val instanceof List) {
                    restrictedModulesStr = String.join(",", (List<String>) val);
                } else if (val instanceof String) {
                    restrictedModulesStr = (String) val;
                }
            } else if (body.get("restricted_modules") != null) {
                Object val = body.get("restricted_modules");
                if (val instanceof List) {
                    restrictedModulesStr = String.join(",", (List<String>) val);
                } else if (val instanceof String) {
                    restrictedModulesStr = (String) val;
                }
            }

            // Prevent privilege escalation: only authenticated admins can set a non-user role
            String requestedRole = "user";
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().matches("ROLE_(ADMIN|SUPER_ADMIN|ULTRA_SUPER_ADMIN)"))) {
                requestedRole = (String) body.getOrDefault("role", "user");
            }

            User user = User.builder()
                .uid((String) body.get("uid"))
                .name((String) body.get("name"))
                .email(((String) body.get("email")).toLowerCase().trim())
                .role(requestedRole)
                .phone((String) body.get("phone"))
                .department((String) body.get("department"))
                .isActive(body.get("is_active") == null || Boolean.parseBoolean(body.get("is_active").toString()))
                .isDemo(Boolean.parseBoolean(body.getOrDefault("is_demo","false").toString()))
                .passwordHash(body.get("password_hash") != null
                    ? (String) body.get("password_hash")
                    : body.get("password") != null ? userService.hashPassword((String) body.get("password")) : null)
                .restrictedModules(restrictedModulesStr)
                .build();
            User createdUser = userService.create(user);
            try {
                String actorId = body.get("createdBy") != null ? (String) body.get("createdBy") : "system";
                String actorName = "Admin";
                String notifMsg = "In the ticketing system you have been logged";
                
                KeyHolder keyHolder = new GeneratedKeyHolder();
                String insertSql = "INSERT INTO notifications (user_id, message, ticket_id, ticket_number, actor_id, actor_name, is_read) VALUES (?, ?, NULL, NULL, ?, ?, 0)";
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, createdUser.getUid());
                    ps.setString(2, notifMsg);
                    ps.setString(3, actorId);
                    ps.setString(4, actorName);
                    return ps;
                }, keyHolder);

                long newNotifId = DbUtil.getGeneratedId(keyHolder);

                Map<String, Object> newNotif = new HashMap<>();
                newNotif.put("id", String.valueOf(newNotifId));
                newNotif.put("user_id", createdUser.getUid());
                newNotif.put("message", notifMsg);
                newNotif.put("ticket_id", null);
                newNotif.put("ticket_number", null);
                newNotif.put("actor_id", actorId);
                newNotif.put("actor_name", actorName);
                newNotif.put("is_read", 0);
                newNotif.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                NotificationController.sendNotification(createdUser.getUid(), newNotif);
            } catch (Exception notifEx) {
                System.err.println("[UserController] Failed to dispatch registration notification: " + notifEx.getMessage());
            }
            return ResponseEntity.status(201).body(serialize(createdUser));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","Failed to create user: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{uid}")
    public ResponseEntity<?> update(
            @PathVariable String uid,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String,Object> body) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: User session not found."));
            }
            String callerUid = userDetails.getUsername();
            boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().matches("ROLE_(ADMIN|SUB_ADMIN|SUPER_ADMIN|ULTRA_SUPER_ADMIN)"));

            // Users can only update their own profile; admins can update any profile
            if (!callerUid.equals(uid) && !isAdmin) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied: Cannot edit another user's profile"));
            }

            User updates = new User();
            if (body.get("name")       != null) updates.setName((String) body.get("name"));
            if (body.get("email")      != null) updates.setEmail((String) body.get("email"));
            
            // Only admins can modify the role field
            if (body.get("role") != null) {
                if (isAdmin) {
                    updates.setRole((String) body.get("role"));
                } else {
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied: Only administrators can modify roles"));
                }
            }
            
            if (body.get("phone")      != null) updates.setPhone((String) body.get("phone"));
            if (body.get("department") != null) updates.setDepartment((String) body.get("department"));
            if (body.get("is_active")  != null) updates.setIsActive(Boolean.parseBoolean(body.get("is_active").toString()));
            if (body.get("password")   != null) updates.setPasswordHash(userService.hashPassword((String) body.get("password")));
            if (body.get("password_hash") != null) updates.setPasswordHash((String) body.get("password_hash"));
            if (body.get("restrictedModules") != null) {
                Object val = body.get("restrictedModules");
                if (val instanceof List) {
                    updates.setRestrictedModules(String.join(",", (List<String>) val));
                } else if (val instanceof String) {
                    updates.setRestrictedModules((String) val);
                }
            } else if (body.get("restricted_modules") != null) {
                Object val = body.get("restricted_modules");
                if (val instanceof List) {
                    updates.setRestrictedModules(String.join(",", (List<String>) val));
                } else if (val instanceof String) {
                    updates.setRestrictedModules((String) val);
                }
            }
            return ResponseEntity.ok(serialize(userService.update(uid, updates)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","Failed to update user: " + e.getMessage()));
        }
    }

    @DeleteMapping("/users/{uid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ULTRA_SUPER_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String uid) {
        userService.softDelete(uid);
        return ResponseEntity.ok(Map.of("success",true));
    }

    @PostMapping("/users/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        String confirmNewPassword = body.get("confirmNewPassword");

        if (currentPassword == null || newPassword == null || confirmNewPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));
        }

        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: User session not found."));
        }

        String headerUid = userDetails.getUsername();

        Optional<User> userOpt = userService.findByUid(headerUid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found."));
        }

        User user = userOpt.get();

        // 1. Verify current password
        Optional<User> authUserOpt = userService.authenticate(user.getEmail(), currentPassword);
        if (authUserOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Incorrect current password."));
        }

        // 2. Ensure new password and confirm password match
        if (!newPassword.equals(confirmNewPassword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password and confirm password do not match."));
        }

        // 3. Prevent weak or invalid passwords based on existing password policies
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 6 characters."));
        }

        try {
            // 4. Encrypt/hash passwords using the current security implementation
            String newHash = userService.hashPassword(newPassword);
            
            User updates = new User();
            updates.setPasswordHash(newHash);
            userService.update(user.getUid(), updates);

            // 5. (Audit logging skipped - password reset is a user self-service action)

            // 6. Send password change email notification
            String emailBody = emailService.buildTemplate(
                "Password Changed",
                null,
                "<p>Hello " + user.getName() + ",</p>" +
                "<p>The password for your Manage My Desk account has been successfully changed.</p>" +
                "<p>If you did not make this change, please contact your administrator immediately.</p>",
                null
            );
            emailService.sendAsync(user.getEmail(), "Manage My Desk - Password Reset Successful", emailBody);

            return ResponseEntity.ok(Map.of("success", true, "message", "Password reset successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to reset password: " + e.getMessage()));
        }
    }

    private Map<String,Object> serialize(User u) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",          String.valueOf(u.getId()));
        m.put("uid",         u.getUid());
        m.put("name",        u.getName());
        m.put("email",       u.getEmail());
        m.put("role",        u.getRole());
        m.put("phone",       u.getPhone());
        m.put("department",  u.getDepartment());
        m.put("is_active",   u.getIsActive());
        m.put("is_demo",     u.getIsDemo());
        m.put("created_at",  u.getCreatedAt());
        m.put("last_login",  u.getLastLogin());

        List<String> modulesList = new ArrayList<>();
        if (u.getRestrictedModules() != null && !u.getRestrictedModules().isBlank()) {
            modulesList = Arrays.asList(u.getRestrictedModules().split(","));
        }
        m.put("restrictedModules", modulesList);
        m.put("restricted_modules", u.getRestrictedModules() != null ? u.getRestrictedModules() : "");
        return m;
    }
}
