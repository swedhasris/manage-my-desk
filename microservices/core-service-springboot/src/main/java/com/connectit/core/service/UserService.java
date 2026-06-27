package com.connectit.core.service;

import com.connectit.core.model.User;
import com.connectit.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User service — all password operations use BCrypt via PasswordEncoder.
 * SimpleHash has been REMOVED from all authentication paths (FIX 3C).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAll() {
        return userRepository.findAllActiveOrderByName();
    }

    public Optional<User> findByUid(String uid) {
        return userRepository.findByUid(uid);
    }

    /**
     * Authenticate by email + password using BCrypt.
     * Supports a migration path: if stored hash looks like a legacy SimpleHash
     * (hex, not starting with $2a$/$2b$), falls back to SimpleHash check and
     * re-hashes on success.
     */
    public Optional<User> authenticate(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(email);
        if (userOpt.isEmpty()) return Optional.empty();

        User user = userOpt.get();
        String storedHash = user.getPasswordHash();
        if (storedHash == null) return Optional.empty();

        boolean valid;
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            // BCrypt hash — use PasswordEncoder
            valid = passwordEncoder.matches(password, storedHash);
        } else {
            // Legacy SimpleHash (hex string) — migration path
            valid = com.connectit.core.util.SimpleHash.hash(password).equals(storedHash);
            if (valid) {
                // Re-hash with BCrypt transparently on next login
                user.setPasswordHash(passwordEncoder.encode(password));
                userRepository.save(user);
            }
        }

        if (!valid) return Optional.empty();
        return Optional.of(user);
    }

    @Transactional
    public User recordLogin(User user) {
        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User create(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User update(String uid, User updates) {
        User existing = userRepository.findByUid(uid)
            .orElseThrow(() -> new RuntimeException("User not found: " + uid));
        if (updates.getName()             != null) existing.setName(updates.getName());
        if (updates.getEmail()            != null) existing.setEmail(updates.getEmail().toLowerCase().trim());
        if (updates.getRole()             != null) existing.setRole(updates.getRole());
        if (updates.getPhone()            != null) existing.setPhone(updates.getPhone());
        if (updates.getDepartment()       != null) existing.setDepartment(updates.getDepartment());
        if (updates.getIsActive()         != null) existing.setIsActive(updates.getIsActive());
        if (updates.getPasswordHash()     != null) existing.setPasswordHash(updates.getPasswordHash());
        if (updates.getRestrictedModules()!= null) existing.setRestrictedModules(updates.getRestrictedModules());
        return userRepository.save(existing);
    }

    /**
     * Hash a plain-text password with BCrypt.
     * Used by controllers when creating/updating users with a new password.
     */
    public String hashPassword(String plainText) {
        return passwordEncoder.encode(plainText);
    }

    @Transactional
    public void softDelete(String uid) {
        userRepository.findByUid(uid).ifPresent(u -> {
            u.setIsActive(false);
            userRepository.save(u);
        });
    }

    public List<User> findAgents() {
        return userRepository.findByRoleInAndIsActiveTrue(
            List.of("agent", "admin", "super_admin", "ultra_super_admin", "sub_admin")
        );
    }
}
