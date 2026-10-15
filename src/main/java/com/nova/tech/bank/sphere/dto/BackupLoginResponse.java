package com.nova.tech.bank.sphere.dto;

public record BackupLoginResponse(
        String accessToken,
        String role,
        long expiresInSeconds,
        int remainingBackupCodes
) {
}
