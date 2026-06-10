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
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body) {
        String email    = body.get("email");
        String password = body.get("password");

        if (email == null || password == null)
            return ResponseEntity.badRequest().body(Map.of("error","Email and password required"));

        Optional<User> userOpt = userService.authenticate(email.toLowerCase().trim(), password);
        if (userOpt.isEmpty())
            return ResponseEntity.status(401).body(Map.of("error","Invalid email or password"));

        User user = userService.recordLogin(userOpt.get());
        String token = jwtUtil.generate(user.getUid(), user.getEmail(), user.getRole());

        return ResponseEntity.ok(Map.of(
            "id",       String.valueOf(user.getId()),
            "uid",      user.getUid(),
            "name",     user.getName(),
            "email",    user.getEmail(),
            "role",     user.getRole(),
            "phone",    user.getPhone() != null ? user.getPhone() : "",
            "token",    token
        ));
    }
}
