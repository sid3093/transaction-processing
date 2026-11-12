package com.sid.bank.service;

import com.sid.bank.entity.Account;
import com.sid.bank.entity.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static com.sid.bank.service.TestAccounts.account;
import static org.junit.jupiter.api.Assertions.*;

class TransactionValidatorTest {

    private final TransactionValidator validator = new TransactionValidator();

    // @ParameterizedTest runs the same test once per value
    @ParameterizedTest
    @ValueSource(strings = {"0.01", "500.00", "1000000.00"})
    void amountsUpToTheLimitAreAllowed(String amount) {
        assertTrue(validator.withinLimit(new BigDecimal(amount)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000.01", "5000000.00"})
    void amountsOverTheLimitAreRejected(String amount) {
        Optional<String> failure = validator.withinLimit(new BigDecimal(amount));
        assertTrue(failure.isPresent());
        assertTrue(failure.get().contains("maximum"));
    }

    @Test
    void activeAccountPasses() {
        assertTrue(validator.isActive(account(1, "0")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FROZEN", "CLOSED"})
    void inactiveAccountIsRejected(String status) {
        Account frozen = account(1, "0");
        frozen.setStatus(AccountStatus.valueOf(status));
        assertEquals("Account 1 is " + status, validator.isActive(frozen).orElseThrow());
    }

    @Test
    void sameAccountIsRejected() {
        Account a = account(1, "100");
        assertTrue(validator.differentAccounts(a, a).isPresent());
        assertTrue(validator.differentAccounts(a, account(2, "100")).isEmpty());
    }

    @Test
    void currencyMismatchIsRejected() {
        Account inr = account(1, "100", "INR");
        Account usd = account(2, "100", "USD");
        assertEquals("Currency mismatch: INR vs USD", validator.sameCurrency(inr, usd).orElseThrow());
        assertTrue(validator.sameCurrency(inr, account(3, "0", "INR")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "99.99", "100.00"})
    void withdrawingUpToTheBalanceIsAllowed(String amount) {
        assertTrue(validator.hasFunds(account(1, "100.00"), new BigDecimal(amount)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.01", "500.00"})
    void withdrawingMoreThanTheBalanceIsRejected(String amount) {
        assertEquals("Insufficient funds",
                validator.hasFunds(account(1, "100.00"), new BigDecimal(amount)).orElseThrow());
    }

    @Test
    void transferReportsTheFirstFailingRule() {
        Account from = account(1, "10.00", "INR");
        from.setStatus(AccountStatus.FROZEN);
        Account to = account(2, "0", "USD");
        // Several rules fail; the inactive account is checked before currency and funds.
        assertEquals("Account 1 is FROZEN",
                validator.checkTransfer(new BigDecimal("50.00"), from, to).orElseThrow());
    }
}
