package com.vaultcore.ledger.dto;

import com.vaultcore.ledger.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AccountRequest {
    @NotNull
    private AccountType accountType;
}
