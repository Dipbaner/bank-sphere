package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.MessageResponse;
import com.nova.tech.bank.sphere.dto.RegisterRequest;
import com.nova.tech.bank.sphere.dto.ResendRequest;
import com.nova.tech.bank.sphere.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Registration successful. Please verify your email."));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified. You can now log in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resend(@Valid @RequestBody ResendRequest req) {
        authService.resendVerification(req.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If the account exists and is not verified, a new link has been sent."));
    }
}
