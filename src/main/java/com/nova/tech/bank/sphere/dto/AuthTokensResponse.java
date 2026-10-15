package com.nova.tech.bank.sphere.dto;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String role,
        long expiresInSeconds
) {
}
