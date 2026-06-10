package com.connectit.core.config;

import com.connectit.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(req);
        if (token != null && jwtUtil.isValid(token)) {
            String uid   = jwtUtil.getUid(token);
            String role  = jwtUtil.getRole(token);
            var auth = new UsernamePasswordAuthenticationToken(
                uid, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // Also support x-user-uid header (backward compat with React frontend)
        String uid = req.getHeader("x-user-uid");
        if (uid != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findByUid(uid).ifPresent(user -> {
                var auth = new UsernamePasswordAuthenticationToken(
                    uid, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
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
