package com.nova.tech.bank.sphere.security;

import java.time.Instant;

public record AuthenticatedUser(
        Long id,
        String email,
        String jti,
        Instant expiresAt) {}