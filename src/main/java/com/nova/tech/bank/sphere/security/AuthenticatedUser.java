package com.nova.tech.bank.sphere.security;

public record AuthenticatedUser(
        Long id, String email) { }
