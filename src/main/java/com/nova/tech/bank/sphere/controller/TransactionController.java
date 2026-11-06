package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.MoneyRequest;
import com.nova.tech.bank.sphere.dto.TransactionResponse;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.StepUpService;
import com.nova.tech.bank.sphere.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class TransactionController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;

    @PostMapping("/{accountNumber}/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable("accountNumber") String accountNumber,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {

        return respond(transactionService.deposit(me, accountNumber, request.getAmount(),
                request.getDescription(), idempotencyKey));
    }

    @PostMapping("/{accountNumber}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable("accountNumber") String accountNumber,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(value = StepUpService.HEADER, required = false) String stepUpToken,
            @Valid @RequestBody MoneyRequest request) {

        return respond(transactionService.withdraw(me, accountNumber, request.getAmount(),
                request.getDescription(), idempotencyKey, stepUpToken));
    }

    /** 201 for a new transaction, 200 plus a header when the original result is replayed. */
    private ResponseEntity<TransactionResponse> respond(TransactionService.Result result) {
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(TransactionResponse.forAccount(result.transaction(), result.accountNumber()));
    }
}