package com.connectit.core.controller;

import com.connectit.core.config.JwtUtil;
import com.connectit.core.model.User;
import com.connectit.core.service.UserService;
import com.connectit.core.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body, HttpServletRequest request) {
        String email    = body.get("email");
        String password = body.get("password");

        if (email == null || password == null)
            return ResponseEntity.badRequest().body(Map.of("error","Email and password required"));

        log.info("Login attempt for user: {}", email);

        try {
            Optional<User> userOpt = userService.authenticate(email.toLowerCase().trim(), password);
            if (userOpt.isEmpty()) {
                log.warn("Authentication failed: invalid credentials for: {}", email);
                return ResponseEntity.status(401).body(Map.of("error","Invalid email or password"));
            }

            User user = userOpt.get();
            User loggedIn = userService.recordLogin(user);
            String token = jwtUtil.generate(loggedIn.getUid(), loggedIn.getEmail(), loggedIn.getRole());

            log.info("Authentication successful for user: {}", email);

            // Fetch IP and user agent for notification
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }
            String userAgent = request.getHeader("User-Agent");

            // Send successful login security email notification asynchronously
            try {
                String bodyHtml = 
                    "<p>Hello <strong>" + loggedIn.getName() + "</strong>,</p>" +
                    "<p>A successful login to your Ticklora account was detected.</p>" +
                    "<table style='width:100%; border-collapse:collapse; margin:16px 0; font-family:sans-serif; font-size:13px;'>" +
                    "<tr><td style='padding:8px; border:1px solid #e2e8f0; font-weight:bold; width:35%; background:#f8fafc;'>Email</td><td style='padding:8px; border:1px solid #e2e8f0;'>" + loggedIn.getEmail() + "</td></tr>" +
                    "<tr><td style='padding:8px; border:1px solid #e2e8f0; font-weight:bold; background:#f8fafc;'>Role</td><td style='padding:8px; border:1px solid #e2e8f0;'>" + loggedIn.getRole() + "</td></tr>" +
                    "<tr><td style='padding:8px; border:1px solid #e2e8f0; font-weight:bold; background:#f8fafc;'>IP Address</td><td style='padding:8px; border:1px solid #e2e8f0;'>" + (ipAddress != null ? ipAddress : "Unknown") + "</td></tr>" +
                    "<tr><td style='padding:8px; border:1px solid #e2e8f0; font-weight:bold; background:#f8fafc;'>Device/Browser</td><td style='padding:8px; border:1px solid #e2e8f0;'>" + (userAgent != null ? userAgent : "Unknown") + "</td></tr>" +
                    "</table>" +
                    "<p>If this was not you, please contact your administrator immediately.</p>" +
                    "<p>Regards,<br/>Manage My Desk Security Team</p>";

                String emailTemplate = emailService.buildTemplate("Successful Login Detected", null, bodyHtml, null);
                emailService.sendAsync(loggedIn.getEmail(), "[Security Alert] Successful Login Detected", emailTemplate);
            } catch (Exception mailEx) {
                log.error("Failed to send login notification email", mailEx);
            }

            return ResponseEntity.ok(Map.of(
                "id",       String.valueOf(loggedIn.getId()),
                "uid",      loggedIn.getUid(),
                "name",     loggedIn.getName(),
                "email",    loggedIn.getEmail(),
                "role",     loggedIn.getRole(),
                "phone",    loggedIn.getPhone() != null ? loggedIn.getPhone() : "",
                "token",    token
            ));
        } catch (Exception ex) {
            log.error("Authentication failed", ex);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    @PostMapping("/demo-login")
    public ResponseEntity<?> demoLogin(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role is required"));
        }

        java.util.Map<String, String> emailMap = java.util.Map.of(
            "user", "user@technosprint.net",
            "agent", "agent@technosprint.net",
            "admin", "admin@technosprint.net",
            "super_admin", "ulter@technosprint.net",
            "ultra_super_admin", "arun.g@technosprint.net",
            "sub_admin", "admin@technosprint.net"
        );

        String email = emailMap.get(role);
        if (email == null) {
            email = "demo-" + role + "@connectit.local";
        }

        String password = "ultra_super_admin".equals(role) ? "Poland@01" : "Password123!";

        return login(java.util.Map.of("email", email, "password", password), request);
    }
}
