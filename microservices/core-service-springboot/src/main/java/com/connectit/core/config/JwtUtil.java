package com.connectit.core.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * JWT utility — generates and validates signed JWTs.
 *
 * Tokens include:
 *  - sub  : user uid
 *  - email: user email
 *  - role : user role (informational only — authoritative role is always from DB)
 *  - iss  : issuer ("ticklora")
 *  - iat  : issued-at
 *  - exp  : expiration
 */
@Component
public class JwtUtil {

    private static final String ISSUER = "ticklora";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /** Cached key — computed once from secret. */
    private volatile Key cachedKey;

    private Key key() {
        if (cachedKey == null) {
            synchronized (this) {
                if (cachedKey == null) {
                    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length < 32) {
                        byte[] padded = new byte[32];
                        System.arraycopy(bytes, 0, padded, 0, bytes.length);
                        cachedKey = Keys.hmacShaKeyFor(padded);
                    } else {
                        cachedKey = Keys.hmacShaKeyFor(bytes);
                    }
                }
            }
        }
        return cachedKey;
    }

    public String generate(String uid, String email, String role) {
        String safeUid   = uid   != null ? uid   : "";
        String safeEmail = email != null ? email : "";
        String safeRole  = role  != null ? role  : "user";
        return Jwts.builder()
            .subject(safeUid)
            .issuer(ISSUER)
            .claims(Map.of("email", safeEmail, "role", safeRole))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(key())
            .compact();
    }

    /**
     * Parses and fully validates the token:
     *  - Signature (HMAC-SHA)
     *  - Expiry
     *  - Issuer
     *
     * Throws JwtException if any check fails.
     */
    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(key().getEncoded()))
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /** Returns true only if signature, expiry, and issuer are all valid. */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Extract individual claims — parse once via isValid gate in the filter
    public String getUid(String token)   { return parse(token).getSubject(); }
    public String getRole(String token)  { return (String) parse(token).get("role"); }
    public String getEmail(String token) { return (String) parse(token).get("email"); }
}
