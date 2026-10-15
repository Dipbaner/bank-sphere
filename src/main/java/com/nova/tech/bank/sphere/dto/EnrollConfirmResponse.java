package com.nova.tech.bank.sphere.dto;

import java.util.List;

public record EnrollConfirmResponse(
        List<String> backupCodes,
        String accessToken,
        String role,
        long expiresInSeconds
) {
}
