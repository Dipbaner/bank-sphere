package com.nova.tech.bank.sphere.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackupLoginResponse(
        String accessToken,
        String refreshToken,
        String role,
        long expiresInSeconds,
        int remainingBackupCodes
) {

    public BackupLoginResponse withoutRefreshToken() {
        return new BackupLoginResponse(accessToken, null, role, expiresInSeconds, remainingBackupCodes);
    }
}
