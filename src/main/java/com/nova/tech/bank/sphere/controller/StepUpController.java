package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.StepUpRequest;
import com.nova.tech.bank.sphere.dto.StepUpResponse;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.StepUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/step-up")
@RequiredArgsConstructor
public class StepUpController {

    private final StepUpService stepUpService;

    @PostMapping
    public ResponseEntity<StepUpResponse> issue(@AuthenticationPrincipal AuthenticatedUser me,
                                                @Valid @RequestBody StepUpRequest req) {
        return ResponseEntity.ok(stepUpService.issue(me, req));
    }
}