package com.vaultcore.ledger.controller;

import com.vaultcore.ledger.domain.User;
import com.vaultcore.ledger.dto.AccountRequest;
import com.vaultcore.ledger.dto.AccountResponse;
import com.vaultcore.ledger.dto.SeedRequest;
import com.vaultcore.ledger.dto.SeedResponse;
import com.vaultcore.ledger.dto.TransactionHistoryResponse;
import com.vaultcore.ledger.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AccountRequest request
    ) {
        return accountService.createAccount(user.getId(), request.getAccountType());
    }

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> getBalance(
            @AuthenticationPrincipal User user,
            @PathVariable UUID accountId
    ) {
        BigDecimal balance = accountService.getBalance(accountId, user.getId());
        return Map.of(
                "accountId", accountId,
                "balance", balance
        );
    }

    @GetMapping("/{accountId}/transactions")
    public Page<TransactionHistoryResponse> getTransactionHistory(
            @AuthenticationPrincipal User user,
            @PathVariable UUID accountId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return accountService.getTransactionHistory(accountId, user.getId(), pageable);
    }

    @PostMapping("/{accountId}/seed")
    @ResponseStatus(HttpStatus.CREATED)
    public SeedResponse seedBalance(
            @AuthenticationPrincipal User user,
            @PathVariable UUID accountId,
            @Valid @RequestBody SeedRequest request
    ) {
        return accountService.seedBalance(accountId, request.getAmount(), user.getId());
    }
}
