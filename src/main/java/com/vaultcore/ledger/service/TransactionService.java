package com.vaultcore.ledger.service;

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

        Optional<Transaction> existingTransaction =
                transactionRepository.findByIdempotencyKey(idempotencyKey);

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        UUID firstLockId;
        UUID secondLockId;

        // Ensure consistent lock ordering
        if (fromAccountId.compareTo(toAccountId) < 0) {
            firstLockId = fromAccountId;
            secondLockId = toAccountId;
        } else {
            firstLockId = toAccountId;
            secondLockId = fromAccountId;
        }

        // Acquire locks
        Account firstAccount = accountRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Account secondAccount = accountRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        // Map accounts correctly
        Account fromAccount =
                fromAccountId.equals(firstLockId) ? firstAccount : secondAccount;

        Account toAccount =
                toAccountId.equals(firstLockId) ? firstAccount : secondAccount;

        // Check balance after locking
        BigDecimal balance = balanceService.getBalance(fromAccountId);

        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        // Create transaction
        Transaction transaction = new Transaction();

        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setReferenceId(referenceId);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);

        // Debit entry
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setTransaction(transaction);
        debitEntry.setAccount(fromAccount);
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setAmount(amount);

        ledgerEntryRepository.save(debitEntry);

        // Credit entry
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setTransaction(transaction);
        creditEntry.setAccount(toAccount);
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setAmount(amount);

        ledgerEntryRepository.save(creditEntry);

        // Complete transaction
        transaction.setStatus(TransactionStatus.COMPLETED);

        return transactionRepository.save(transaction);
    }
}