package com.connectit.core.controller;

import com.connectit.core.config.JwtUtil;
import com.connectit.core.model.User;
import com.connectit.core.service.UserService;
import com.connectit.core.util.SimpleHash;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body) {
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
}
