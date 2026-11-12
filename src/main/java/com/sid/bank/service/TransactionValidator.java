package com.sid.bank.service;

import com.sid.bank.entity.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

// Each business rule is a small method returning a failure reason (or empty).
// Small rules = easy to read, easy to unit test.
@Component
public class TransactionValidator {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    public Optional<String> checkDeposit(BigDecimal amount, Account to) {
        return firstFailure(withinLimit(amount), isActive(to));
    }

    public Optional<String> checkWithdrawal(BigDecimal amount, Account from) {
        return firstFailure(withinLimit(amount), isActive(from), hasFunds(from, amount));
    }

    public Optional<String> checkTransfer(BigDecimal amount, Account from, Account to) {
        return firstFailure(
                differentAccounts(from, to),
                withinLimit(amount),
                isActive(from),
                isActive(to),
                sameCurrency(from, to),
                hasFunds(from, amount));
    }

    Optional<String> withinLimit(BigDecimal amount) {
        return amount.compareTo(MAX_AMOUNT) > 0
                ? Optional.of("Amount exceeds the maximum of " + MAX_AMOUNT)
                : Optional.empty();
    }

    Optional<String> isActive(Account account) {
        return account.isActive()
                ? Optional.empty()
                : Optional.of("Account " + account.getId() + " is " + account.getStatus());
    }

    Optional<String> differentAccounts(Account from, Account to) {
        return from.getId().equals(to.getId())
                ? Optional.of("Source and destination accounts must differ")
                : Optional.empty();
    }

    Optional<String> sameCurrency(Account from, Account to) {
        return from.getCurrency().equals(to.getCurrency())
                ? Optional.empty()
                : Optional.of("Currency mismatch: " + from.getCurrency() + " vs " + to.getCurrency());
    }

    Optional<String> hasFunds(Account from, BigDecimal amount) {
        return from.getBalance().compareTo(amount) >= 0
                ? Optional.empty()
                : Optional.of("Insufficient funds");
    }

    @SafeVarargs
    private Optional<String> firstFailure(Optional<String>... checks) {
        return Stream.of(checks).flatMap(Optional::stream).findFirst();
    }
}
