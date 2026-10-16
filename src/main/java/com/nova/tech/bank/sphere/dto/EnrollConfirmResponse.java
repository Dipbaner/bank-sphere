package com.nova.tech.bank.sphere.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollConfirmResponse(
        List<String> backupCodes,
        String accessToken,
        String refreshToken,
        String role,
        long expiresInSeconds
) {

    public EnrollConfirmResponse withoutRefreshToken() {
        return new EnrollConfirmResponse(backupCodes, accessToken, null, role, expiresInSeconds);
    }
}
