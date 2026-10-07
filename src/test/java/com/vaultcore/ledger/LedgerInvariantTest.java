package com.vaultcore.ledger;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.LedgerEntry;
import com.vaultcore.ledger.domain.LedgerEntryType;
import com.vaultcore.ledger.domain.Transaction;
import com.vaultcore.ledger.domain.TransactionStatus;
import com.vaultcore.ledger.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerInvariantTest extends AbstractLedgerTest {

    @Test
    void transferCreatesExactlyOneDebitAndOneCreditOfEqualAmount() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "500");

        Transaction transaction = transactionService.createTransaction(
                owner.getId(),
                "inv-idem-" + UUID.randomUUID(),
                "inv-ref-" + UUID.randomUUID(),
                new BigDecimal("250"),
                from.getId(),
                to.getId()
        );

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionIdWithAccount(transaction.getId());
        assertThat(entries).hasSize(2);
        assertThat(ledgerEntryRepository.countByTransaction_Id(transaction.getId())).isEqualTo(2);

        LedgerEntry debit = entryOfType(entries, LedgerEntryType.DEBIT);
        LedgerEntry credit = entryOfType(entries, LedgerEntryType.CREDIT);

        // Exactly one of each, pointing at the correct accounts.
        assertThat(debit.getAccount().getId()).isEqualTo(from.getId());
        assertThat(credit.getAccount().getId()).isEqualTo(to.getId());

        // Both legs carry the same amount.
        assertThat(debit.getAmount()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(credit.getAmount()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
    }

    @Test
    void accountBalanceEqualsCreditsMinusDebits() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "1000");

        transactionService.createTransaction(owner.getId(),
                "inv-idem-" + UUID.randomUUID(), "inv-ref-" + UUID.randomUUID(),
                new BigDecimal("300"), from.getId(), to.getId());
        transactionService.createTransaction(owner.getId(),
                "inv-idem-" + UUID.randomUUID(), "inv-ref-" + UUID.randomUUID(),
                new BigDecimal("200"), from.getId(), to.getId());

        assertThat(balanceService.getBalance(from.getId())).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(balanceService.getBalance(to.getId())).isEqualByComparingTo(new BigDecimal("500"));

        assertBalanceMatchesLedger(from.getId());
        assertBalanceMatchesLedger(to.getId());
    }

    @Test
    void transactionHistoryReturnsOwnedEntriesWithCounterparty() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "1000");

        Transaction transfer = transactionService.createTransaction(owner.getId(),
                "inv-idem-" + UUID.randomUUID(), "inv-ref-" + UUID.randomUUID(),
                new BigDecimal("300"), from.getId(), to.getId());

        var page = accountService.getTransactionHistory(
                from.getId(), owner.getId(), PageRequest.of(0, 10));

        var row = page.getContent().stream()
                .filter(r -> r.getTransactionId().equals(transfer.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("transfer missing from history"));

        assertThat(row.getEntryType()).isEqualTo("DEBIT");
        assertThat(row.getAmount()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(row.getCounterpartyAccountNumber()).isEqualTo(to.getAccountNumber());
    }

    private void assertBalanceMatchesLedger(UUID accountId) {
        BigDecimal credits = ledgerEntryRepository
                .sumAmountByAccountIdAndEntryType(accountId, LedgerEntryType.CREDIT);
        BigDecimal debits = ledgerEntryRepository
                .sumAmountByAccountIdAndEntryType(accountId, LedgerEntryType.DEBIT);
        BigDecimal expected = credits.subtract(debits);

        assertThat(balanceService.getBalance(accountId))
                .as("balance must equal SUM(credits) - SUM(debits) for account %s", accountId)
                .isEqualByComparingTo(expected);
    }

    private LedgerEntry entryOfType(List<LedgerEntry> entries, LedgerEntryType type) {
        return entries.stream()
                .filter(e -> e.getEntryType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type + " entry"));
    }
}
