package com.vaultcore.ledger.service;

import com.vaultcore.ledger.config.IdempotencyCache;
import com.vaultcore.ledger.config.LedgerMetrics;
import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final int MAX_REFERENCE_ID_LENGTH = 50;

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final BalanceService balanceService;
    private final IdempotencyCache idempotencyCache;
    private final LedgerMetrics ledgerMetrics;

    @Transactional
    public Transaction createTransaction(
            UUID ownerId,
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {
        validateRequest(idempotencyKey, referenceId, amount, fromAccountId, toAccountId);

        return ledgerMetrics.recordTransactionDuration(() ->
                processTransaction(ownerId, idempotencyKey, referenceId, amount,
                        fromAccountId, toAccountId));
    }

    private Transaction processTransaction(
            UUID ownerId,
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {

        // 1. Fast path: Redis cache. This is an optimization only; correctness is
        // enforced by the unique constraint on transactions.idempotency_key below.
        Optional<Transaction> cached = lookupCachedTransaction(idempotencyKey);
        if (cached.isPresent()) {
            ledgerMetrics.recordIdempotentHit();
            return cached.get();
        }

        // 2. Load and validate/authorize before taking any locks.
        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + fromAccountId));
        Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + toAccountId));

        if (fromAccount.getUser() == null
                || ownerId == null
                || !ownerId.equals(fromAccount.getUser().getId())) {
            throw new AccessDeniedException(
                    "Source account does not belong to the authenticated user");
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different");
        }

        requireActive(fromAccount, "Source");
        requireActive(toAccount, "Destination");

        // 3. Deterministic lock ordering prevents deadlocks between concurrent transfers.
        boolean fromLocksFirst = fromAccountId.compareTo(toAccountId) < 0;
        UUID firstLockId = fromLocksFirst ? fromAccountId : toAccountId;
        UUID secondLockId = fromLocksFirst ? toAccountId : fromAccountId;

        Account firstLocked = accountRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLockId));
        Account secondLocked = accountRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLockId));

        fromAccount = fromLocksFirst ? firstLocked : secondLocked;
        toAccount = fromLocksFirst ? secondLocked : firstLocked;

        // 4. Re-check idempotency AFTER acquiring the locks. Concurrent duplicate requests
        // share the same accounts, so they serialize on these locks; the second request then
        // observes the first request's committed transaction instead of inserting a duplicate.
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            idempotencyCache.record(idempotencyKey, existing.get().getId());
            ledgerMetrics.recordIdempotentHit();
            return existing.get();
        }

        BigDecimal balance = balanceService.getBalance(fromAccountId);

        if (balance.compareTo(amount) < 0) {
            ledgerMetrics.recordTransactionFailed();
            throw new IllegalArgumentException("Insufficient balance");
        }

        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setReferenceId(referenceId);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction = transactionRepository.save(transaction);

        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setTransaction(transaction);
        debitEntry.setAccount(fromAccount);
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setAmount(amount);
        ledgerEntryRepository.save(debitEntry);

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setTransaction(transaction);
        creditEntry.setAccount(toAccount);
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setAmount(amount);
        ledgerEntryRepository.save(creditEntry);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        idempotencyCache.record(idempotencyKey, transaction.getId());
        ledgerMetrics.recordTransactionCreated();
        ledgerMetrics.recordTransactionSucceeded();

        return transaction;
    }

    private Optional<Transaction> lookupCachedTransaction(String idempotencyKey) {
        if (!idempotencyCache.isDuplicate(idempotencyKey)) {
            return Optional.empty();
        }
        return idempotencyCache.getTransactionId(idempotencyKey)
                .flatMap(transactionRepository::findById);
    }

    private void requireActive(Account account, String role) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    role + " account is not active: " + account.getId());
        }
    }

    private void validateRequest(
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (fromAccountId == null || toAccountId == null) {
            throw new IllegalArgumentException("Source and destination accounts are required");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference id is required");
        }

        if (referenceId.length() > MAX_REFERENCE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Reference id must be at most " + MAX_REFERENCE_ID_LENGTH + " characters");
        }
    }
}
