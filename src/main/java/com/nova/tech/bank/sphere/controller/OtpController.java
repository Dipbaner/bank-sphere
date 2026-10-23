package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.*;
import com.nova.tech.bank.sphere.security.AccessTokenBlacklist;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.security.RefreshCookie;
import com.nova.tech.bank.sphere.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final RefreshCookie refreshCookie;
    private final AccessTokenBlacklist blacklist;

    @PostMapping("/enroll")
    public ResponseEntity<EnrollResponse> enroll(@AuthenticationPrincipal AuthenticatedUser me) {
        return ResponseEntity.ok(otpService.startEnrollment(me.id()));
    }

    @PostMapping("/enroll/confirm")
    public ResponseEntity<EnrollConfirmResponse> confirm(@AuthenticationPrincipal AuthenticatedUser me,
                                                         @Valid @RequestBody OtpRequest req) {
// confirm()
        EnrollConfirmResponse r = otpService.confirmEnrollment(me.id(), req.getOtp());
        blacklist.revoke(me.jti(), me.expiresAt());
        return ResponseEntity.ok().headers(refreshCookie.set(r.refreshToken()))
                .body(refreshCookie.exposeInBody() ? r : r.withoutRefreshToken());
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthTokensResponse> verify(@AuthenticationPrincipal AuthenticatedUser me,
                                                     @Valid @RequestBody OtpRequest req) {
        // verify()
        AuthTokensResponse r = otpService.verifyLogin(me.id(), req.getOtp());
        blacklist.revoke(me.jti(), me.expiresAt());
        return ResponseEntity.ok().headers(refreshCookie.set(r.refreshToken()))
                .body(refreshCookie.exposeInBody() ? r : r.withoutRefreshToken());
    }

    @PostMapping("/backup")
    public ResponseEntity<BackupLoginResponse> backup(@AuthenticationPrincipal AuthenticatedUser me,
                                                      @Valid @RequestBody BackupCodeRequest req) {
// backup()
        BackupLoginResponse r = otpService.verifyBackupCode(me.id(), req.getCode());
        blacklist.revoke(me.jti(), me.expiresAt());
        return ResponseEntity.ok().headers(refreshCookie.set(r.refreshToken()))
                .body(refreshCookie.exposeInBody() ? r : r.withoutRefreshToken());
    }
}