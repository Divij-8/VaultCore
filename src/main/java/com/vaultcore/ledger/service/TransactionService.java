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

    @Transactional
public Transaction createTransaction(
        String idempotencyKey,
        String referenceId,
        BigDecimal amount,
        UUID fromAccountId,
        UUID toAccountId
) {

    if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
        throw new IllegalArgumentException("Duplicate transaction");
    }

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Amount must be > 0");
    }

    if (fromAccountId.equals(toAccountId)) {
        throw new IllegalArgumentException("Same account transfer not allowed");
    }

    Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new IllegalArgumentException("From account not found"));

    Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new IllegalArgumentException("To account not found"));

    Transaction transaction = new Transaction();
    transaction.setIdempotencyKey(idempotencyKey);
    transaction.setReferenceId(referenceId);
    transaction.setAmount(amount);
    transaction.setStatus(TransactionStatus.PENDING);

    transaction = transactionRepository.save(transaction);

    try {

        LedgerEntry debit = new LedgerEntry();
        debit.setTransaction(transaction);
        debit.setAccount(fromAccount);
        debit.setEntryType(LedgerEntryType.DEBIT);
        debit.setAmount(amount);

        LedgerEntry credit = new LedgerEntry();
        credit.setTransaction(transaction);
        credit.setAccount(toAccount);
        credit.setEntryType(LedgerEntryType.CREDIT);
        credit.setAmount(amount);

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        transaction.setStatus(TransactionStatus.SUCCESS);

    } catch (Exception e) {
        transaction.setStatus(TransactionStatus.FAILED);
        throw e;
    }

    return transactionRepository.save(transaction);
}
}
