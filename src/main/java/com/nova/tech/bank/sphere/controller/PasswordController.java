package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.ChangePasswordRequest;
import com.nova.tech.bank.sphere.dto.ForgotPasswordRequest;
import com.nova.tech.bank.sphere.dto.MessageResponse;
import com.nova.tech.bank.sphere.dto.ResetPasswordRequest;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.PasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordService.requestReset(req.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for this email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> reset(@Valid @RequestBody ResetPasswordRequest req) {
        passwordService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(new MessageResponse(
                "Password updated. Please log in with your new password."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> change(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @Valid @RequestBody ChangePasswordRequest req) {
        passwordService.changePassword(me, req);
        return ResponseEntity.ok(new MessageResponse("Password changed. Please log in again."));
    }
}
