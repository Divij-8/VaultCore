package com.vaultcore.ledger;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionServiceTest extends AbstractLedgerTest {

    @Test
    void shouldTransferMoneyCorrectly() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "1000");

        transactionService.createTransaction(
                owner.getId(),
                "idem-" + UUID.randomUUID(),
                "ref-" + UUID.randomUUID(),
                new BigDecimal("500"),
                from.getId(),
                to.getId()
        );

        assertThat(balanceService.getBalance(from.getId())).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(balanceService.getBalance(to.getId())).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                BigDecimal.ZERO, from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                new BigDecimal("-10"), from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAmount() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                null, from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSelfTransfer() {
        User owner = createUser();
        Account account = createAccount(owner);
        seedBalance(owner, account, "100");

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                new BigDecimal("10"), account.getId(), account.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void rejectsNonexistentAccount() {
        User owner = createUser();
        Account account = createAccount(owner);
        seedBalance(owner, account, "100");

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                new BigDecimal("10"), account.getId(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsBlankOrOversizedIdempotencyKey() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "  ", "ref-" + UUID.randomUUID(),
                new BigDecimal("10"), from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "k".repeat(256), "ref-" + UUID.randomUUID(),
                new BigDecimal("10"), from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedReferenceId() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "r".repeat(51),
                new BigDecimal("10"), from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTransferWhenBalanceInsufficient() {
        User owner = createUser();
        Account from = createAccount(owner);
        Account to = createAccount(owner);
        seedBalance(owner, from, "50");

        assertThatThrownBy(() -> transactionService.createTransaction(
                owner.getId(), "idem-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                new BigDecimal("100"), from.getId(), to.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(balanceService.getBalance(from.getId())).isEqualByComparingTo("50");
        assertThat(balanceService.getBalance(to.getId())).isEqualByComparingTo("0");
    }
}
