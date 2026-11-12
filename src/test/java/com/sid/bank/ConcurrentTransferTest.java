package com.sid.bank;

import com.sid.bank.dto.CreateAccountRequest;
import com.sid.bank.dto.TransactionRequest;
import com.sid.bank.entity.Account;
import com.sid.bank.entity.BankTransaction;
import com.sid.bank.entity.TransactionStatus;
import com.sid.bank.entity.TransactionType;
import com.sid.bank.service.AccountService;
import com.sid.bank.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Integration test: runs the real service against the real Postgres in Docker.
// Fires many requests at the SAME accounts at the SAME time.
@SpringBootTest
class ConcurrentTransferTest {

    @Autowired
    AccountService accountService;

    @Autowired
    TransactionService transactionService;

    @Test
    void hundredParallelTransfersInBothDirectionsKeepMoneyConstant() throws Exception {
        Long a = newAccountWith("10000.00");
        Long b = newAccountWith("1000.00");
        BigDecimal totalBefore = balance(a).add(balance(b));

        // 50 transfers A -> B of 10.00 and 50 transfers B -> A of 5.00, all at once.
        // Opposite directions are what cause deadlocks if locks are taken in the wrong order.
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<BankTransaction>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TransactionRequest request = (i % 2 == 0)
                    ? new TransactionRequest(TransactionType.TRANSFER, new BigDecimal("10.00"), a, b)
                    : new TransactionRequest(TransactionType.TRANSFER, new BigDecimal("5.00"), b, a);
            results.add(pool.submit(() -> transactionService.process(request)));
        }
        for (Future<BankTransaction> result : results) {
            assertEquals(TransactionStatus.COMPLETED, result.get(60, TimeUnit.SECONDS).getStatus());
        }
        pool.shutdown();

        // A: 10000 - 50*10 + 50*5 = 9750    B: 1000 + 50*10 - 50*5 = 1250
        assertMoney("9750.00", balance(a));
        assertMoney("1250.00", balance(b));
        assertMoney(totalBefore.toPlainString(), balance(a).add(balance(b)));
    }

    @Test
    void parallelWithdrawalsNeverOverdraw() throws Exception {
        Long account = newAccountWith("100.00");

        // 50 people try to withdraw 10.00 at once, but only 100.00 exists:
        // exactly 10 must succeed and 40 must be rejected.
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<BankTransaction>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            TransactionRequest request =
                    new TransactionRequest(TransactionType.WITHDRAWAL, new BigDecimal("10.00"), account, null);
            results.add(pool.submit(() -> transactionService.process(request)));
        }
        int completed = 0;
        int rejected = 0;
        for (Future<BankTransaction> result : results) {
            if (result.get(60, TimeUnit.SECONDS).getStatus() == TransactionStatus.COMPLETED) {
                completed++;
            } else {
                rejected++;
            }
        }
        pool.shutdown();

        assertEquals(10, completed);
        assertEquals(40, rejected);
        assertMoney("0.00", balance(account));
    }

    private Long newAccountWith(String amount) {
        Account account = accountService.create(new CreateAccountRequest("Concurrency Test", "INR"));
        transactionService.process(
                new TransactionRequest(TransactionType.DEPOSIT, new BigDecimal(amount), null, account.getId()));
        return account.getId();
    }

    private BigDecimal balance(Long id) {
        return accountService.get(id).getBalance();
    }

    // compareTo ignores scale, so 9750.0 and 9750.00 count as equal
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }
}
