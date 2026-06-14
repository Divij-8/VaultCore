package com.vaultcore.ledger.service;

import com.vaultcore.ledger.config.IdempotencyCache;
import com.vaultcore.ledger.config.LedgerMetrics;
import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final BalanceService balanceService;
    private final IdempotencyCache idempotencyCache;
    private final LedgerMetrics ledgerMetrics;

    @Transactional
    public Transaction createTransaction(
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {

        return processTransaction(
                idempotencyKey,
                referenceId,
                amount,
                fromAccountId,
                toAccountId
        );
    }

    public Transaction processTransaction(
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {

        return ledgerMetrics.recordTransactionDuration(() -> {

            if (idempotencyCache.isDuplicate(idempotencyKey)) {
                ledgerMetrics.recordIdempotentHit();
                Optional<UUID> cachedId = idempotencyCache.getTransactionId(idempotencyKey);
                if (cachedId.isPresent()) {
                    return transactionRepository.findById(cachedId.get())
                            .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
                }
            }

            Optional<Transaction> existingTransaction =
                    transactionRepository.findByIdempotencyKey(idempotencyKey);

            if (existingTransaction.isPresent()) {
                idempotencyCache.record(idempotencyKey, existingTransaction.get().getId());
                ledgerMetrics.recordIdempotentHit();
                return existingTransaction.get();
            }

            UUID firstLockId;
            UUID secondLockId;

            if (fromAccountId.compareTo(toAccountId) < 0) {
                firstLockId = fromAccountId;
                secondLockId = toAccountId;
            } else {
                firstLockId = toAccountId;
                secondLockId = fromAccountId;
            }

            Account firstAccount = accountRepository.findByIdWithLock(firstLockId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            Account secondAccount = accountRepository.findByIdWithLock(secondLockId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            Account fromAccount =
                    fromAccountId.equals(firstLockId) ? firstAccount : secondAccount;

            Account toAccount =
                    toAccountId.equals(firstLockId) ? firstAccount : secondAccount;

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
        });
    }
}