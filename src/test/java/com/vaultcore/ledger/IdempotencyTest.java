package com.vaultcore.ledger;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.Transaction;
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

class IdempotencyTest extends AbstractLedgerTest {

    @Test
    void repeatingTheSameIdempotencyKeyAppliesTheTransferOnce() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "1000");

        String idempotencyKey = "idem-" + UUID.randomUUID();

        Transaction first = transactionService.createTransaction(
                owner.getId(), idempotencyKey, "ref-" + UUID.randomUUID(),
                new BigDecimal("300"), from.getId(), to.getId());

        BigDecimal balanceAfterFirst = balanceService.getBalance(from.getId());
        assertThat(balanceAfterFirst).isEqualByComparingTo(new BigDecimal("700"));

        // Same key, repeated (a different referenceId must still return the original transaction).
        Transaction second = transactionService.createTransaction(
                owner.getId(), idempotencyKey, "ref-" + UUID.randomUUID(),
                new BigDecimal("300"), from.getId(), to.getId());

        assertThat(second.getId()).isEqualTo(first.getId());

        // No extra ledger entries, balances moved exactly once.
        assertThat(ledgerEntryRepository.countByTransaction_Id(first.getId())).isEqualTo(2);
        assertThat(balanceService.getBalance(from.getId())).isEqualByComparingTo(new BigDecimal("700"));
        assertThat(balanceService.getBalance(to.getId())).isEqualByComparingTo(new BigDecimal("300"));
    }

    @Test
    void concurrentDuplicateRequestsProduceExactlyOneTransaction() throws Exception {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "1000");

        String idempotencyKey = "idem-dup-" + UUID.randomUUID();
        int attempts = 12;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Transaction>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            Callable<Transaction> attempt = () -> {
                startGate.await();
                return transactionService.createTransaction(
                        owner.getId(), idempotencyKey, "ref-" + UUID.randomUUID(),
                        new BigDecimal("300"), from.getId(), to.getId());
            };
            futures.add(executor.submit(attempt));
        }

        startGate.countDown();

        Set<UUID> transactionIds = new HashSet<>();
        for (Future<Transaction> future : futures) {
            Transaction result = future.get(30, TimeUnit.SECONDS);
            assertThat(result).isNotNull();
            transactionIds.add(result.getId());
        }
        executor.shutdownNow();

        // All concurrent duplicates resolved to the same single transaction.
        assertThat(transactionIds).hasSize(1);

        UUID transactionId = transactionIds.iterator().next();
        assertThat(ledgerEntryRepository.countByTransaction_Id(transactionId)).isEqualTo(2);

        // Money moved exactly once.
        assertThat(balanceService.getBalance(from.getId())).isEqualByComparingTo(new BigDecimal("700"));
        assertThat(balanceService.getBalance(to.getId())).isEqualByComparingTo(new BigDecimal("300"));
    }
}
