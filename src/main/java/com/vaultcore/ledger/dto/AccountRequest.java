package com.vaultcore.ledger.dto;

import com.vaultcore.ledger.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AccountRequest {
    @NotNull
    private UUID userId;

    @NotNull
    private AccountType accountType;
}
