package com.connectit.core.config;

import com.connectit.core.model.User;
import com.connectit.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * JWT Authentication Filter — validates every incoming request.
 *
 * Checks performed (in order):
 *  1. Token exists and starts with "Bearer "
 *  2. Token signature is valid (HMAC-SHA)
 *  3. Token is not expired
 *  4. User still exists in the database
 *  5. User account is active (isActive = true)
 *  6. User role from DB (not from token) is used for authorities
 *
 * The legacy x-user-uid / user_id bypass has been REMOVED — it was
 * an unauthenticated identity-injection vulnerability.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(req);

        if (token != null) {
            try {
                // 1 & 2 & 3 — signature + expiry validation
                if (!jwtUtil.isValid(token)) {
                    log.warn("JWT validation failed for request: {}", req.getRequestURI());
                    chain.doFilter(req, res);
                    return;
                }

                String uid = jwtUtil.getUid(token);
                if (uid == null || uid.isBlank()) {
                    log.warn("JWT has empty uid claim");
                    chain.doFilter(req, res);
                    return;
                }

                // 4 & 5 — user exists and is active
                Optional<User> userOpt = userRepository.findByUid(uid);
                if (userOpt.isEmpty()) {
                    log.warn("JWT uid '{}' not found in database", uid);
                    chain.doFilter(req, res);
                    return;
                }

                User user = userOpt.get();
                if (Boolean.FALSE.equals(user.getIsActive())) {
                    log.warn("JWT uid '{}' belongs to a deactivated account", uid);
                    chain.doFilter(req, res);
                    return;
                }

                // 6 — role from DB (authoritative), not from token
                String dbRole = user.getRole() != null ? user.getRole().toUpperCase() : "USER";

                var auth = new UsernamePasswordAuthenticationToken(
                    uid, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + dbRole))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ex) {
                // Any parse or validation exception — reject silently, request proceeds as anonymous
                log.warn("JWT processing error for request {}: {}", req.getRequestURI(), ex.getMessage());
            }
        }

        chain.doFilter(req, res);
    }

    private String extractToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
