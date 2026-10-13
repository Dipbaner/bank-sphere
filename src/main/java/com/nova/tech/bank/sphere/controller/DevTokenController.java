package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("dev")
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class DevTokenController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    // TEMPORARY: swaps a pre-auth token for an access token WITHOUT any OTP check.
    @PostMapping("/dev-exchange")
    public ResponseEntity<Map<String, Object>> exchange(@AuthenticationPrincipal AuthenticatedUser me) {
        var user = userRepository.findById(me.id()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "accessToken", jwtService.generateAccessToken(user),
                "role", user.getRole().name(),
                "expiresInSeconds", jwtService.accessSeconds()));
    }
}
