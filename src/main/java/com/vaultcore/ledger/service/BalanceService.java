package com.vaultcore.ledger.service;

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

    public BigDecimal getBalance(UUID accountId) {
        BigDecimal credits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, LedgerEntryType.CREDIT);
        BigDecimal debits = ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, LedgerEntryType.DEBIT);
        return credits.subtract(debits);
    }
}
