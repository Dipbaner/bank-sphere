package com.nova.tech.bank.sphere.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String role,
        long expiresInSeconds
) {

    public AuthTokensResponse withoutRefreshToken() {
        return new AuthTokensResponse(accessToken, null, role, expiresInSeconds);
    }
}
