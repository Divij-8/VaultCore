package com.vaultcore.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SeedResponse {
    private UUID transactionId;
    private UUID accountId;
    private BigDecimal amount;
    private String status;
}
