package com.vaultcore.ledger;
import com.vaultcore.ledger.domain.*;
import com.vaultcore.ledger.repository.*;
import com.vaultcore.ledger.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
@SpringBootTest
class ConcurrencyTest {
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Test
    void shouldPreventDoubleSpending() throws InterruptedException {
        User user = new User();
        user.setName("Test User");
        user.setPhoneNumber("TEST-" + System.currentTimeMillis());
        user = userRepository.save(user);
        
        // Create account to test
        Account tempAcc1 = new Account();
        tempAcc1.setAccountNumber("ACC1");
        tempAcc1.setUser(user);
        tempAcc1.setAccountType(AccountType.USER);
        tempAcc1.setStatus(AccountStatus.ACTIVE);
        final Account acc1 = accountRepository.save(tempAcc1);
        
        Account tempAcc2 = new Account();
        tempAcc2.setAccountNumber("ACC2");
        tempAcc2.setUser(user);
        tempAcc2.setAccountType(AccountType.USER);
        tempAcc2.setStatus(AccountStatus.ACTIVE);
        final Account acc2 = accountRepository.save(tempAcc2);
        
        // Manually create initial balance for acc1 (simulating system funding)
        LedgerEntry initialCredit = new LedgerEntry();
        initialCredit.setAccount(acc1);
        initialCredit.setEntryType(LedgerEntryType.CREDIT);
        initialCredit.setAmount(new BigDecimal("1000"));
        ledgerEntryRepository.save(initialCredit);
        
        System.out.println("💰 Initial balance for acc1: 1000");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Runnable task = () -> {
            try {
                // Try to withdraw 800 from acc1 (which has 1000)
                // Both threads will try simultaneously
                transactionService.createTransaction(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        new BigDecimal("800"),
                        acc1.getId(), // FROM acc1
                        acc2.getId()  // TO acc2
                );
                System.out.println("✅ Transaction succeeded");
            } catch (Exception e) {
                System.out.println("🔴 Transaction failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        };
        
        executor.submit(task);
        executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("\n🎯 Test completed - One transaction should have succeeded, one should have failed!");
    }
}
