package com.vaultcore.ledger.service;

import com.vaultcore.ledger.config.LedgerMetrics;
import com.vaultcore.ledger.domain.LedgerEntryType;
import com.vaultcore.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BalanceService {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerMetrics ledgerMetrics;

    public BigDecimal getBalance(UUID accountId) {
        return ledgerMetrics.recordBalanceQueryDuration(() -> {
            ledgerMetrics.recordBalanceQuery();
            BigDecimal credits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, LedgerEntryType.CREDIT);
            BigDecimal debits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, LedgerEntryType.DEBIT);
            return credits.subtract(debits);
        });
    }
}
