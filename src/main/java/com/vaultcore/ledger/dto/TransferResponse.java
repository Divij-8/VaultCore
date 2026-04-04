package com.vaultcore.ledger.dto;
import com.vaultcore.ledger.domain.TransactionStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferResponse {
    private UUID transactionId;
    private String referenceId;
    private BigDecimal amount;
    private TransactionStatus status;
}
