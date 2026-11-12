package com.sid.bank.service;

import com.sid.bank.dto.TransactionRequest;
import com.sid.bank.entity.*;
import com.sid.bank.exception.InvalidRequestException;
import com.sid.bank.exception.ResourceNotFoundException;
import com.sid.bank.repository.AccountRepository;
import com.sid.bank.repository.LedgerEntryRepository;
import com.sid.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransactionService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final TransactionValidator validator;

    public TransactionService(AccountRepository accounts, TransactionRepository transactions,
                              LedgerEntryRepository ledger, TransactionValidator validator) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.validator = validator;
    }

    // @Transactional: everything in here commits together or not at all.
    @Transactional
    public BankTransaction process(TransactionRequest request) {
        return switch (request.type()) {
            case DEPOSIT -> deposit(request);
            case WITHDRAWAL -> withdraw(request);
            case TRANSFER -> transfer(request);
        };
    }

    private BankTransaction deposit(TransactionRequest req) {
        Account to = load(require(req.toAccountId(), "toAccountId is required for DEPOSIT"));
        BigDecimal amount = req.amount();

        Optional<String> failure = validator.checkDeposit(amount, to);
        if (failure.isPresent()) {
            return reject(TransactionType.DEPOSIT, amount, to.getCurrency(), null, to, failure.get());
        }

        to.credit(amount);
        BankTransaction tx = complete(TransactionType.DEPOSIT, amount, to.getCurrency(), null, to);
        ledger.save(new LedgerEntry(tx, to, EntryDirection.CREDIT, amount));
        return tx;
    }

    private BankTransaction withdraw(TransactionRequest req) {
        Account from = load(require(req.fromAccountId(), "fromAccountId is required for WITHDRAWAL"));
        BigDecimal amount = req.amount();

        Optional<String> failure = validator.checkWithdrawal(amount, from);
        if (failure.isPresent()) {
            return reject(TransactionType.WITHDRAWAL, amount, from.getCurrency(), from, null, failure.get());
        }

        from.debit(amount);
        BankTransaction tx = complete(TransactionType.WITHDRAWAL, amount, from.getCurrency(), from, null);
        ledger.save(new LedgerEntry(tx, from, EntryDirection.DEBIT, amount));
        return tx;
    }

    private BankTransaction transfer(TransactionRequest req) {
        Long fromId = require(req.fromAccountId(), "fromAccountId is required for TRANSFER");
        Long toId = require(req.toAccountId(), "toAccountId is required for TRANSFER");
        BigDecimal amount = req.amount();

        Account from = load(fromId);
        Account to = load(toId);

        Optional<String> failure = validator.checkTransfer(amount, from, to);
        if (failure.isPresent()) {
            return reject(TransactionType.TRANSFER, amount, from.getCurrency(), from, to, failure.get());
        }

        from.debit(amount);
        to.credit(amount);
        BankTransaction tx = complete(TransactionType.TRANSFER, amount, from.getCurrency(), from, to);
        ledger.save(new LedgerEntry(tx, from, EntryDirection.DEBIT, amount));
        ledger.save(new LedgerEntry(tx, to, EntryDirection.CREDIT, amount));
        return tx;
    }

    private Account load(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + id + " not found"));
    }

    private BankTransaction complete(TransactionType type, BigDecimal amount, String currency,
                                     Account from, Account to) {
        return transactions.save(new BankTransaction(type, TransactionStatus.COMPLETED, amount, currency,
                from, to, null));
    }

    // A rejected request is still recorded, so there is an audit trail.
    private BankTransaction reject(TransactionType type, BigDecimal amount, String currency,
                                   Account from, Account to, String reason) {
        return transactions.save(new BankTransaction(type, TransactionStatus.REJECTED, amount, currency,
                from, to, reason));
    }

    private static Long require(Long id, String message) {
        if (id == null) {
            throw new InvalidRequestException(message);
        }
        return id;
    }
}
