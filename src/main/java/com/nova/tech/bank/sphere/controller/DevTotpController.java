package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Profile("dev")
@RequestMapping("/api/dev/totp")
@RequiredArgsConstructor
public class DevTotpController {

    private final TotpService totpService;

    @Value("${app.totp.issuer}")
    private String issuer;

    // TEMPORARY. Returns a fresh secret; nothing is stored.
    @GetMapping("/new")
    public Map<String, String> newSecret(@AuthenticationPrincipal AuthenticatedUser me) {
        String secret = totpService.generateSecret();
        return Map.of("secret", secret,
                "otpauthUri", totpService.buildOtpAuthUri(issuer, me.email(), secret));
    }

    // TEMPORARY. Checks a typed code against a secret you pass in.
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        var step = totpService.verify(body.get("secret"), body.get("code"), 0);
        return Map.of("valid", step.isPresent());
    }
}
