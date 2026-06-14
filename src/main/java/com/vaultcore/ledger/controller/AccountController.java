package com.vaultcore.ledger.controller;

import com.vaultcore.ledger.dto.AccountRequest;
import com.vaultcore.ledger.dto.AccountResponse;
import com.vaultcore.ledger.dto.SeedRequest;
import com.vaultcore.ledger.dto.SeedResponse;
import com.vaultcore.ledger.dto.TransactionHistoryResponse;
import com.vaultcore.ledger.service.AccountService;
import com.vaultcore.ledger.service.BalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceService balanceService;
    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        return accountService.createAccount(request.getUserId(), request.getAccountType());
    }

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> getBalance(
            @PathVariable UUID accountId
    ) {
        BigDecimal balance = balanceService.getBalance(accountId);
        return Map.of(
                "accountId", accountId,
                "balance", balance
        );
    }

    @GetMapping("/{accountId}/transactions")
    public Page<TransactionHistoryResponse> getTransactionHistory(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return accountService.getTransactionHistory(accountId, pageable);
    }

    @PostMapping("/{accountId}/seed")
    @ResponseStatus(HttpStatus.CREATED)
    public SeedResponse seedBalance(
            @PathVariable UUID accountId,
            @Valid @RequestBody SeedRequest request
    ) {
        return accountService.seedBalance(accountId, request.getAmount());
    }
}
