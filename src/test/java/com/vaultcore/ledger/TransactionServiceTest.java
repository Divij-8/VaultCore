package com.vaultcore.ledger;
import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.repository.*;
import com.vaultcore.ledger.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TransactionServiceTest {
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private BalanceService balanceService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldTransferMoneyCorrectly() {
        User user = new User();
        user.setName("Test User");
        user.setPhoneNumber("+1" + System.currentTimeMillis());
        user = userRepository.save(user);

        Account acc1 = new Account();
        acc1.setAccountNumber("ACC" + System.nanoTime());
        acc1.setAccountType(AccountType.USER);
        acc1.setStatus(AccountStatus.ACTIVE);
        acc1.setUser(user);
        acc1 = accountRepository.save(acc1);
        
        Account acc2 = new Account();
        acc2.setAccountNumber("ACC" + (System.nanoTime() + 1));
        acc2.setAccountType(AccountType.USER);
        acc2.setStatus(AccountStatus.ACTIVE);
        acc2.setUser(user);
        acc2 = accountRepository.save(acc2);
        
        transactionService.createTransaction(
                "idem-" + UUID.randomUUID(),
                "ref-" + UUID.randomUUID(),
                new BigDecimal("500"),
                acc1.getId(),
                acc2.getId()
        );
        
        var balance1 = balanceService.getBalance(acc1.getId());
        var balance2 = balanceService.getBalance(acc2.getId());
        
        assertThat(balance1).isEqualByComparingTo(new BigDecimal("-500"));
        assertThat(balance2).isEqualByComparingTo(new BigDecimal("500"));
    }
}
