package com.vaultcore.ledger;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.AccountStatus;
import com.vaultcore.ledger.domain.AccountType;
import com.vaultcore.ledger.domain.User;
import com.vaultcore.ledger.repository.AccountRepository;
import com.vaultcore.ledger.repository.LedgerEntryRepository;
import com.vaultcore.ledger.repository.TransactionRepository;
import com.vaultcore.ledger.repository.UserRepository;
import com.vaultcore.ledger.service.AccountService;
import com.vaultcore.ledger.service.BalanceService;
import com.vaultcore.ledger.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Base class for ledger integration tests.
 *
 * <p>Deliberately NOT {@code @Transactional}: tests that spawn threads need their setup data
 * to be committed and visible to those threads. Each test uses unique phone/account numbers
 * so committed fixtures never collide across runs.
 */
@SpringBootTest
abstract class AbstractLedgerTest {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected AccountRepository accountRepository;
    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    protected TransactionService transactionService;
    @Autowired
    protected AccountService accountService;
    @Autowired
    protected BalanceService balanceService;

    protected User createUser() {
        User user = new User();
        user.setName("Test User");
        // phone_number/account_number are varchar(20); derive short unique values from a UUID.
        user.setPhoneNumber("+" + shortId(17));
        // Raw value is fine: these tests exercise the ledger, not password hashing.
        user.setPassword("password");
        user.setRoles("USER");
        return userRepository.save(user);
    }

    protected Account createAccount(User owner) {
        Account account = new Account();
        account.setAccountNumber("ACC" + shortId(16));
        account.setUser(owner);
        account.setAccountType(AccountType.USER);
        account.setStatus(AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }

    /** Credits the account through the real seed flow (SYSTEM debit + user credit). */
    protected void seedBalance(User owner, Account account, String amount) {
        accountService.seedBalance(account.getId(), new BigDecimal(amount), owner.getId());
    }

    private static String shortId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
