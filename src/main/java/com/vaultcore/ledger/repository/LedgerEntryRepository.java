package com.vaultcore.ledger.repository;

import com.vaultcore.ledger.domain.LedgerEntry;
import com.vaultcore.ledger.domain.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("""
        SELECT COALESCE(SUM(le.amount), 0)
        FROM LedgerEntry le
        WHERE le.account.id = :accountId AND le.entryType = :type
    """)
    BigDecimal sumAmountByAccountIdAndEntryType(UUID accountId, LedgerEntryType type);
}