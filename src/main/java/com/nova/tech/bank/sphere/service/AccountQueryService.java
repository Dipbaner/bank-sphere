package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.AccountResponse;
import com.nova.tech.bank.sphere.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountQueryService {

    private final AccountRepository accounts;

    public AccountQueryService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listFor(Long userId) {
        return accounts.findByUserIdOrderByIdAsc(userId).stream()
                .map(a -> new AccountResponse(a.getAccountNumber(), a.getType().name(),
                        a.getStatus().name(), a.getBalance(), a.getCreatedAt()))
                .toList();
    }
}