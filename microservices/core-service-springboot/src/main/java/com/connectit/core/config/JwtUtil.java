package com.connectit.core.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private Key key() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // Pad to minimum 256 bits
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generate(String uid, String email, String role) {
        String safeUid = uid != null ? uid : "";
        String safeEmail = email != null ? email : "";
        String safeRole = role != null ? role : "user";
        return Jwts.builder()
            .subject(safeUid)
            .claims(Map.of("email", safeEmail, "role", safeRole))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(key())
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8).length >= 32
                ? secret.getBytes(StandardCharsets.UTF_8)
                : padSecret(secret.getBytes(StandardCharsets.UTF_8))
        )).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { parse(token); return true; } catch (Exception e) { return false; }
    }

    public String getUid(String token)   { return parse(token).getSubject(); }
    public String getRole(String token)  { return (String) parse(token).get("role"); }
    public String getEmail(String token) { return (String) parse(token).get("email"); }

    private byte[] padSecret(byte[] in) {
        byte[] out = new byte[32];
        System.arraycopy(in, 0, out, 0, in.length);
        return out;
    }
}
