package com.nova.tech.bank.sphere.dto;

import java.util.List;

public record EnrollConfirmResponse(
        List<String> backupCodes,
        String accessToken,
        String refreshToken,
        String role,
        long expiresInSeconds
) {
}
