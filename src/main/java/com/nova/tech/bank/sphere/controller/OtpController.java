package com.nova.tech.bank.sphere.controller;


import com.nova.tech.bank.sphere.dto.*;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/enroll")
    public ResponseEntity<EnrollResponse> enroll(@AuthenticationPrincipal AuthenticatedUser me) {
        return ResponseEntity.ok(otpService.startEnrollment(me.id()));
    }

    @PostMapping("/enroll/confirm")
    public ResponseEntity<EnrollConfirmResponse> confirm(@AuthenticationPrincipal AuthenticatedUser me,
                                                         @Valid @RequestBody OtpRequest req) {
        return ResponseEntity.ok(otpService.confirmEnrollment(me.id(), req.getOtp()));
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthTokensResponse> verify(@AuthenticationPrincipal AuthenticatedUser me,
                                                     @Valid @RequestBody OtpRequest req) {
        return ResponseEntity.ok(otpService.verifyLogin(me.id(), req.getOtp()));
    }

    @PostMapping("/backup")
    public ResponseEntity<BackupLoginResponse> backup(@AuthenticationPrincipal AuthenticatedUser me,
                                                      @Valid @RequestBody BackupCodeRequest req) {
        return ResponseEntity.ok(otpService.verifyBackupCode(me.id(), req.getCode()));
    }
}
