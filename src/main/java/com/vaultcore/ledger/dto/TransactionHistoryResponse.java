package com.vaultcore.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TransactionHistoryResponse {
    private UUID transactionId;
    private String referenceId;
    private BigDecimal amount;
    private String entryType;
    private String counterpartyAccountNumber;
    private UUID counterpartyAccountId;
    private String status;
    private Instant createdAt;
}
