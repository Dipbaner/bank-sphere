package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.*;
import com.nova.tech.bank.sphere.exception.InvalidRefreshTokenException;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.security.RefreshCookie;
import com.nova.tech.bank.sphere.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest body) {

        String raw = firstNonBlank(cookieToken, body == null ? null : body.getRefreshToken());
        if (raw == null) throw new InvalidRefreshTokenException();

        AuthTokensResponse r = authService.refresh(raw);
        return ResponseEntity.ok().headers(refreshCookie.set(r.refreshToken()))
                .body(refreshCookie.exposeInBody() ? r : r.withoutRefreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal AuthenticatedUser me,
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            @RequestBody(required = false) LogoutRequest body) {

        authService.logout(me, firstNonBlank(cookieToken, body == null ? null : body.getRefreshToken()));
        return ResponseEntity.ok().headers(refreshCookie.clear()).body(new MessageResponse("Logged out"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAll(@AuthenticationPrincipal AuthenticatedUser me) {
        authService.logoutAll(me);
        return ResponseEntity.ok().headers(refreshCookie.clear())
                .body(new MessageResponse("Logged out from all devices"));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}