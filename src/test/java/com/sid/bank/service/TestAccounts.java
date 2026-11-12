package com.sid.bank.service;

import com.sid.bank.entity.Account;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

final class TestAccounts {

    private TestAccounts() {
    }

    static Account account(long id, String balance, String currency) {
        Account account = new Account("ACC" + id, "Test User", currency);
        account.credit(new BigDecimal(balance));
        // The id is normally set by the database; in unit tests we set it ourselves.
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    static Account account(long id, String balance) {
        return account(id, balance, "INR");
    }
}
