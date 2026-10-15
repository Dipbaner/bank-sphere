package com.nova.tech.bank.sphere.dto;

public record AuthTokensResponse(
        String accessToken,
        String role,
        long expiresInSeconds
) {
}
