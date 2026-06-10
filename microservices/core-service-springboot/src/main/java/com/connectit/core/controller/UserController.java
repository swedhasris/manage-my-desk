package com.connectit.core.controller;

import com.connectit.core.model.User;
import com.connectit.core.service.UserService;
import com.connectit.core.util.SimpleHash;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
            User user = User.builder()
                .uid((String) body.get("uid"))
                .name((String) body.get("name"))
                .email(((String) body.get("email")).toLowerCase().trim())
                .role((String) body.getOrDefault("role","user"))
                .phone((String) body.get("phone"))
                .department((String) body.get("department"))
                .isActive(body.get("is_active") == null || Boolean.parseBoolean(body.get("is_active").toString()))
                .isDemo(Boolean.parseBoolean(body.getOrDefault("is_demo","false").toString()))
                .passwordHash(body.get("password_hash") != null
                    ? (String) body.get("password_hash")
                    : body.get("password") != null ? SimpleHash.hash((String) body.get("password")) : null)
                .build();
            return ResponseEntity.status(201).body(serialize(userService.create(user)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","Failed to create user: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{uid}")
    public ResponseEntity<?> update(@PathVariable String uid, @RequestBody Map<String,Object> body) {
        try {
            User updates = new User();
            if (body.get("name")       != null) updates.setName((String) body.get("name"));
            if (body.get("email")      != null) updates.setEmail((String) body.get("email"));
            if (body.get("role")       != null) updates.setRole((String) body.get("role"));
            if (body.get("phone")      != null) updates.setPhone((String) body.get("phone"));
            if (body.get("department") != null) updates.setDepartment((String) body.get("department"));
            if (body.get("is_active")  != null) updates.setIsActive(Boolean.parseBoolean(body.get("is_active").toString()));
            if (body.get("password")   != null) updates.setPasswordHash(SimpleHash.hash((String) body.get("password")));
            if (body.get("password_hash") != null) updates.setPasswordHash((String) body.get("password_hash"));
            return ResponseEntity.ok(serialize(userService.update(uid, updates)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","Failed to update user: " + e.getMessage()));
        }
    }

    @DeleteMapping("/users/{uid}")
    public ResponseEntity<?> delete(@PathVariable String uid) {
        userService.softDelete(uid);
        return ResponseEntity.ok(Map.of("success",true));
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
        return m;
    }
}
