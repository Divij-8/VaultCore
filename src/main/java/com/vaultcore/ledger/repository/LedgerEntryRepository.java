package com.vaultcore.ledger.repository;

import com.vaultcore.ledger.domain.LedgerEntry;
import com.vaultcore.ledger.domain.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("""
        SELECT COALESCE(SUM(le.amount), 0)
        FROM LedgerEntry le
        WHERE le.account.id = :accountId AND le.entryType = :type
    """)
    BigDecimal sumAmountByAccountIdAndEntryType(UUID accountId, LedgerEntryType type);

    Page<LedgerEntry> findByAccount_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    @Query("""
        SELECT le FROM LedgerEntry le
        WHERE le.transaction.id = :transactionId
        AND le.account.id <> :accountId
    """)
    Optional<LedgerEntry> findCounterpartyByTransactionIdAndAccountId(
            UUID transactionId, UUID accountId);
}