package com.vaultcore.ledger;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.AccountStatus;
import com.vaultcore.ledger.domain.AccountType;
import com.vaultcore.ledger.domain.User;
import com.vaultcore.ledger.dto.AccountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationTest extends AbstractLedgerTest {

    @Test
    void createAccountAssignsOwnershipToTheAuthenticatedUser() {
        User alice = createUser();

        AccountResponse response = accountService.createAccount(alice.getId(), AccountType.USER);

        Account created = accountRepository.findById(response.getId()).orElseThrow();
        assertThat(created.getUser().getId()).isEqualTo(alice.getId());
    }

    @Test
    void userCannotReadAnotherUsersBalance() {
        User alice = createUser();
        User bob = createUser();
        Account bobAccount = createAccount(bob);
        seedBalance(bob, bobAccount, "500");

        assertThatThrownBy(() -> accountService.getBalance(bobAccount.getId(), alice.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userCannotReadAnotherUsersTransactionHistory() {
        User alice = createUser();
        User bob = createUser();
        Account bobAccount = createAccount(bob);
        seedBalance(bob, bobAccount, "500");

        assertThatThrownBy(() -> accountService.getTransactionHistory(
                bobAccount.getId(), alice.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userCannotSeedAnotherUsersAccount() {
        User alice = createUser();
        User bob = createUser();
        Account bobAccount = createAccount(bob);

        assertThatThrownBy(() -> accountService.seedBalance(
                bobAccount.getId(), new BigDecimal("1000"), alice.getId()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(balanceService.getBalance(bobAccount.getId())).isEqualByComparingTo("0");
    }

    @Test
    void userCannotTransferFromAnotherUsersAccount() {
        User alice = createUser();
        User bob = createUser();
        Account aliceAccount = createAccount(alice);
        Account bobAccount = createAccount(bob);
        seedBalance(bob, bobAccount, "1000");

        // Alice attempts to drain Bob's account by knowing its id.
        assertThatThrownBy(() -> transactionService.createTransaction(
                alice.getId(),
                "authz-idem-" + UUID.randomUUID(),
                "authz-ref-" + UUID.randomUUID(),
                new BigDecimal("100"),
                bobAccount.getId(),
                aliceAccount.getId()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(balanceService.getBalance(bobAccount.getId())).isEqualByComparingTo("1000");
    }

    @Test
    void transferToClosedAccountIsRejected() {
        User alice = createUser();
        Account source = createAccount(alice);
        Account closed = createAccount(alice);
        seedBalance(alice, source, "1000");

        closed.setStatus(AccountStatus.CLOSED);
        accountRepository.save(closed);

        assertThatThrownBy(() -> transactionService.createTransaction(
                alice.getId(), "closed-idem-" + UUID.randomUUID(),
                "closed-ref-" + UUID.randomUUID(), new BigDecimal("100"),
                source.getId(), closed.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
