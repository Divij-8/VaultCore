package com.vaultcore.ledger;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.LedgerEntryType;
import com.vaultcore.ledger.domain.Transaction;
import com.vaultcore.ledger.domain.TransactionStatus;
import com.vaultcore.ledger.domain.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyTest extends AbstractLedgerTest {

    private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000");
    private static final BigDecimal PER_TRANSFER = new BigDecimal("100");
    private static final int ATTEMPTS = 20; // only 10 can succeed with 1000 available

    @Test
    void concurrentTransfersCannotExceedAvailableBalance() throws Exception {
        User owner = createUser();
        Account source = createAccount(owner);
        Account destination = createAccount(owner);
        seedBalance(owner, source, STARTING_BALANCE.toPlainString());

        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<Transaction>> futures = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            final String idempotencyKey = "conc-idem-" + UUID.randomUUID();
            final String referenceId = "conc-ref-" + UUID.randomUUID();
            Callable<Transaction> attempt = () -> {
                startGate.await();
                try {
                    return transactionService.createTransaction(
                            owner.getId(),
                            idempotencyKey,
                            referenceId,
                            PER_TRANSFER,
                            source.getId(),
                            destination.getId()
                    );
                } catch (RuntimeException rejected) {
                    return null; // insufficient balance / rejected transfer
                }
            };
            futures.add(executor.submit(attempt));
        }

        startGate.countDown();

        List<Transaction> succeeded = new ArrayList<>();
        int failed = 0;
        for (Future<Transaction> future : futures) {
            Transaction result = future.get(30, TimeUnit.SECONDS);
            if (result != null && result.getStatus() == TransactionStatus.COMPLETED) {
                succeeded.add(result);
            } else {
                failed++;
            }
        }
        executor.shutdownNow();

        // Exactly enough transfers succeed to drain the balance, no more and no less.
        assertThat(succeeded).hasSize(10);
        assertThat(failed).isEqualTo(ATTEMPTS - 10);

        // Balances reflect exactly the successful transfers.
        assertThat(balanceService.getBalance(source.getId())).isEqualByComparingTo("0");
        assertThat(balanceService.getBalance(destination.getId()))
                .isEqualByComparingTo(STARTING_BALANCE);

        // Every successful transfer produced exactly one DEBIT and one CREDIT entry.
        assertThat(ledgerEntryRepository.countByAccount_IdAndEntryType(
                source.getId(), LedgerEntryType.DEBIT)).isEqualTo(10);
        assertThat(ledgerEntryRepository.countByAccount_IdAndEntryType(
                destination.getId(), LedgerEntryType.CREDIT)).isEqualTo(10);

        // No duplicated transactions: each success is a distinct transaction with 2 entries.
        Set<UUID> transactionIds = new HashSet<>();
        for (Transaction transaction : succeeded) {
            transactionIds.add(transaction.getId());
            assertThat(ledgerEntryRepository.countByTransaction_Id(transaction.getId())).isEqualTo(2);
        }
        assertThat(transactionIds).hasSize(10);

        // Ledger stays balanced: total debits equal total credits for the transfer entries.
        assertThat(totalAmount(LedgerEntryType.DEBIT, source.getId()))
                .isEqualByComparingTo(totalAmount(LedgerEntryType.CREDIT, destination.getId()));
    }

    private java.math.BigDecimal totalAmount(LedgerEntryType type, UUID accountId) {
        return ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, type);
    }
}
