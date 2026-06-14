package com.vaultcore.ledger.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Component
public class LedgerMetrics {

    private final Counter transactionsTotal;
    private final Counter transactionsSucceeded;
    private final Counter transactionsFailed;
    private final Counter transactionsIdempotent;
    private final Counter balanceQueries;
    private final Counter rateLimitHits;
    private final Timer transactionTimer;
    private final Timer balanceQueryTimer;

    public LedgerMetrics(MeterRegistry registry) {
        this.transactionsTotal = Counter.builder("ledger.transactions.total")
                .description("Total transactions created")
                .register(registry);

        this.transactionsSucceeded = Counter.builder("ledger.transactions.succeeded")
                .description("Transactions completed successfully")
                .register(registry);

        this.transactionsFailed = Counter.builder("ledger.transactions.failed")
                .description("Transactions that failed")
                .register(registry);

        this.transactionsIdempotent = Counter.builder("ledger.transactions.idempotent")
                .description("Duplicate idempotent transactions detected")
                .register(registry);

        this.balanceQueries = Counter.builder("ledger.balance.queries")
                .description("Balance query count")
                .register(registry);

        this.rateLimitHits = Counter.builder("ledger.ratelimit.hits")
                .description("Rate limited requests")
                .register(registry);

        this.transactionTimer = Timer.builder("ledger.transaction.duration")
                .description("Transaction processing time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.balanceQueryTimer = Timer.builder("ledger.balance.query.duration")
                .description("Balance query execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordTransactionCreated() {
        transactionsTotal.increment();
    }

    public void recordTransactionSucceeded() {
        transactionsSucceeded.increment();
    }

    public void recordTransactionFailed() {
        transactionsFailed.increment();
    }

    public void recordIdempotentHit() {
        transactionsIdempotent.increment();
    }

    public void recordBalanceQuery() {
        balanceQueries.increment();
    }

    public void recordRateLimitHit() {
        rateLimitHits.increment();
    }

    public <T> T recordTransactionDuration(DurationCallable<T> callable) {
        return transactionTimer.record(callable::call);
    }

    public void recordTransactionDuration(Runnable runnable) {
        transactionTimer.record(runnable);
    }

    public <T> T recordBalanceQueryDuration(DurationCallable<T> callable) {
        return balanceQueryTimer.record(callable::call);
    }

    @FunctionalInterface
    public interface DurationCallable<T> {
        T call();
    }
}
