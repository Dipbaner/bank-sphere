package com.nova.tech.bank.sphere.dto;

public record EnrollConfirmTimeResponse(
        java.util.List<String> backupCodes,
        String accessToken, String role,
        long expiresInSeconds
) {
}
