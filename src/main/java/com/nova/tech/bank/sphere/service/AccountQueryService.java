package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.AccountResponse;
import com.nova.tech.bank.sphere.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(Long userId) {
        return accountRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(AccountResponse::from)
                .toList();
    }
}