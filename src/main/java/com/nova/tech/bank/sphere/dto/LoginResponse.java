package com.nova.tech.bank.sphere.dto;

public record LoginResponse(
        String status,
        String preAuthToken,
        long expiresInSeconds,
        boolean enrollmentRequired) { }
