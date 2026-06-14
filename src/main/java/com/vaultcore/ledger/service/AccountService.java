package com.vaultcore.ledger.service;

import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.dto.AccountResponse;
import com.vaultcore.ledger.dto.SeedResponse;
import com.vaultcore.ledger.dto.TransactionHistoryResponse;
import com.vaultcore.ledger.repository.AccountRepository;
import com.vaultcore.ledger.repository.LedgerEntryRepository;
import com.vaultcore.ledger.repository.TransactionRepository;
import com.vaultcore.ledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public AccountResponse createAccount(UUID userId, AccountType accountType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setUser(user);
        account.setAccountType(accountType);
        account.setStatus(AccountStatus.ACTIVE);

        account = accountRepository.save(account);

        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getStatus().name()
        );
    }

    @Transactional
    public SeedResponse seedBalance(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        Account systemAccount = accountRepository.findAll().stream()
                .filter(a -> a.getAccountType() == AccountType.SYSTEM)
                .findFirst()
                .orElseGet(() -> {
                    Account sys = new Account();
                    sys.setAccountNumber("SYSTEM");
                    sys.setUser(null);
                    sys.setAccountType(AccountType.SYSTEM);
                    sys.setStatus(AccountStatus.ACTIVE);
                    return accountRepository.save(sys);
                });

        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey("seed-" + UUID.randomUUID());
        transaction.setReferenceId("seed-" + UUID.randomUUID());
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setTransaction(transaction);
        debitEntry.setAccount(systemAccount);
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setAmount(amount);
        ledgerEntryRepository.save(debitEntry);

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setTransaction(transaction);
        creditEntry.setAccount(account);
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setAmount(amount);
        ledgerEntryRepository.save(creditEntry);

        return new SeedResponse(transaction.getId(), accountId, amount, "COMPLETED");
    }

    public Page<TransactionHistoryResponse> getTransactionHistory(UUID accountId, Pageable pageable) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        Page<LedgerEntry> entries = ledgerEntryRepository
                .findByAccount_IdOrderByCreatedAtDesc(accountId, pageable);

        return entries.map(entry -> {
            Transaction tx = entry.getTransaction();

            var counterparty = ledgerEntryRepository
                    .findCounterpartyByTransactionIdAndAccountId(tx.getId(), accountId);

            String counterpartyAccountNumber = counterparty
                    .map(cp -> cp.getAccount().getAccountNumber())
                    .orElse(null);

            UUID counterpartyAccountId = counterparty
                    .map(cp -> cp.getAccount().getId())
                    .orElse(null);

            return new TransactionHistoryResponse(
                    tx.getId(),
                    tx.getReferenceId(),
                    entry.getAmount(),
                    entry.getEntryType().name(),
                    counterpartyAccountNumber,
                    counterpartyAccountId,
                    tx.getStatus().name(),
                    entry.getCreatedAt()
            );
        });
    }

    private String generateAccountNumber() {
        return "ACC" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
