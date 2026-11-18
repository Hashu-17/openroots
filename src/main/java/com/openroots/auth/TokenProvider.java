package com.openroots.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues and verifies short-lived HMAC-signed JWT access tokens for human users. */
@Component
public class TokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration expiration;

    public TokenProvider(
            @Value("${openroots.jwt.secret}") String secret,
            @Value("${openroots.jwt.expiration:PT15M}") Duration expiration) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "openroots.jwt.secret must be a base64 string of at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public String createToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /** Verifies signature and expiry; throws {@link io.jsonwebtoken.JwtException} when invalid. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
