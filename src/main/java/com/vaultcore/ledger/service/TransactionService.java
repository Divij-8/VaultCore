package com.vaultcore.ledger.service;

import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

        if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new IllegalArgumentException("Duplicate transaction request");
        }

        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found"));

        Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found"));

        BigDecimal balance = balanceService.getBalance(fromAccountId);

        if (balance.compareTo(amount) < 0) {
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

        return transactionRepository.save(transaction);
    }
}