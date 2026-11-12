package com.sid.bank.service;

import com.sid.bank.dto.CreateAccountRequest;
import com.sid.bank.entity.Account;
import com.sid.bank.entity.BankTransaction;
import com.sid.bank.exception.ResourceNotFoundException;
import com.sid.bank.repository.AccountRepository;
import com.sid.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    // Constructor injection: Spring passes in the repositories.
    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public Account create(CreateAccountRequest request) {
        String number;
        do {
            number = "ACC" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
        } while (accounts.existsByAccountNumber(number));
        return accounts.save(new Account(number, request.ownerName().trim(), request.currency()));
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> history(Long accountId) {
        get(accountId); // 404 if the account does not exist
        return transactions.findHistory(accountId);
    }
}
