package com.nova.tech.bank.sphere.security;

import com.nova.tech.bank.sphere.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String STAGE_PRE_AUTH = "PRE_AUTH";
    public static final String STAGE_ACCESS = "ACCESS";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.preauth-minutes}")
    private long preAuthMinutes;

    @Value("${app.jwt.access-minutes}")
    private long accessMinutes;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret.trim()));
    }

    public String generatePreAuthToken(User user) {
        return build(user, STAGE_PRE_AUTH, Duration.ofMinutes(preAuthMinutes));
    }

    public String generateAccessToken(User user) {
        return build(user, STAGE_ACCESS, Duration.ofMinutes(accessMinutes));
    }

    public long preAuthSeconds() { return preAuthMinutes * 60; }
    public long accessSeconds()  { return accessMinutes * 60; }

    private String build(User user, String stage, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("stage", stage)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** Throws JwtException (signature, expiry, issuer, format) if the token is not valid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
