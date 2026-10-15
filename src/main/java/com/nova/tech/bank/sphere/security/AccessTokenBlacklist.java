package com.nova.tech.bank.sphere.security;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers logged-out access tokens (by their unique id) until they would have expired anyway. */
@Component
public class AccessTokenBlacklist {

    private final ConcurrentHashMap<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String jti, Instant expiresAt) {
        purgeExpired();
        revoked.put(jti, expiresAt);
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.containsKey(jti);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
