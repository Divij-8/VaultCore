package com.vaultcore.ledger.controller;

import com.vaultcore.ledger.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceService balanceService;

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
}