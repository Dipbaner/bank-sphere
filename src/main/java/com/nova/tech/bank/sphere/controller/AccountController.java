package com.nova.tech.bank.sphere.controller;

import com.nova.tech.bank.sphere.dto.AccountResponse;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import com.nova.tech.bank.sphere.service.AccountQueryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
public class AccountController {

    private final AccountQueryService service;

    public AccountController(AccountQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listFor(user.id());
    }
}
