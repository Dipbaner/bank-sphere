package com.nova.tech.bank.sphere.dto;

public record EnrollResponse(
        String secret,
        String otpauthUri,
        String issuer,
        String accountName
) {
}
