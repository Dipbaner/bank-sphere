package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.MoneyRequest;
import com.nova.tech.bank.sphere.dto.TransactionResponse;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.StepUpService;
import com.nova.tech.bank.sphere.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts/{accountNumber}")
@PreAuthorize("hasRole('CUSTOMER')")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String accountNumber,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody MoneyRequest body) {
        return respond(service.deposit(user, accountNumber, key, body));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String accountNumber,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken,
            @Valid @RequestBody MoneyRequest body) {
        return respond(service.withdraw(user, accountNumber, key, body, stepUpToken));
    }

    private ResponseEntity<TransactionResponse> respond(TransactionService.Outcome o) {
        return ResponseEntity.status(o.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(o.replayed()))
                .body(o.response());
    }
}
