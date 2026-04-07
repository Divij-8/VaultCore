package com.vaultcore.ledger.service;
import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final BalanceService balanceService;
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;
    
    public Transaction createTransaction(
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {
        int attempt = 1;
        
        while (attempt <= MAX_RETRIES) {
            try {
                return processTransaction(idempotencyKey, referenceId, amount, fromAccountId, toAccountId);
            } catch (Exception e) {
                if (isConcurrencyException(e)) {
                    if (attempt == MAX_RETRIES) {
                        log.error("Transaction failed after {} retries: {}", MAX_RETRIES, e.getMessage());
                        throw new RuntimeException("Transaction failed after " + MAX_RETRIES + " retries", e);
                    }
                    
                    log.warn("Concurrency conflict detected, retrying transaction... attempt {}/{}", attempt, MAX_RETRIES);
                    attempt++;
                    
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Transaction retry interrupted", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        
        throw new RuntimeException("Transaction failed unexpectedly");
    }
    
    @Transactional
    public Transaction processTransaction(
            String idempotencyKey,
            String referenceId,
            BigDecimal amount,
            UUID fromAccountId,
            UUID toAccountId
    ) {
        if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new IllegalArgumentException("Duplicate transaction request");
        }
        
        UUID firstLockId;
        UUID secondLockId;
        // Ensure consistent locking order
        if (fromAccountId.compareTo(toAccountId) < 0) {
            firstLockId = fromAccountId;
            secondLockId = toAccountId;
        } else {
            firstLockId = toAccountId;
            secondLockId = fromAccountId;
        }
        // Acquire locks in order
        Account firstAccount = accountRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Account secondAccount = accountRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        // Map back to actual roles
        Account fromAccount = fromAccountId.equals(firstLockId) ? firstAccount : secondAccount;
        Account toAccount = toAccountId.equals(firstLockId) ? firstAccount : secondAccount;
        
        // Check balance AFTER acquiring the lock
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
    
    private boolean isConcurrencyException(Exception e) {
        // Check if it's a DataAccessException (covers most DB concurrency issues)
        if (e instanceof DataAccessException) {
            return true;
        }
        
        // Check exception chain for specific concurrency exceptions
        Throwable cause = e.getCause();
        while (cause != null) {
            String className = cause.getClass().getSimpleName();
            if (className.contains("OptimisticLock") || 
                className.contains("PessimisticLock") ||
                className.contains("Deadlock")) {
                return true;
            }
            cause = cause.getCause();
        }
        
        return false;
    }
}
