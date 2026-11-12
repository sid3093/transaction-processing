package com.sid.bank.service;

import com.sid.bank.dto.TransactionRequest;
import com.sid.bank.entity.*;
import com.sid.bank.exception.InvalidRequestException;
import com.sid.bank.exception.ResourceNotFoundException;
import com.sid.bank.repository.AccountRepository;
import com.sid.bank.repository.LedgerEntryRepository;
import com.sid.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.sid.bank.service.TestAccounts.account;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    // @Mock: fake repositories. They do nothing unless we tell them to.
    @Mock
    AccountRepository accounts;

    @Mock
    TransactionRepository transactions;

    @Mock
    LedgerEntryRepository ledger;

    // @Spy: the REAL validator, so the service uses the real business rules.
    @Spy
    TransactionValidator validator = new TransactionValidator();

    // @InjectMocks: builds a real TransactionService with the fakes above.
    @InjectMocks
    TransactionService service;

    @BeforeEach
    void saveReturnsWhatItWasGiven() {
        // lenient(): not every test reaches transactions.save, and that is fine.
        lenient().when(transactions.save(any(BankTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenAccounts(Account... list) {
        for (Account a : list) {
            when(accounts.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        }
    }

    private static TransactionRequest transfer(String amount, Long from, Long to) {
        return new TransactionRequest(TransactionType.TRANSFER, new BigDecimal(amount), from, to);
    }

    // ---------- happy paths ----------

    @Test
    void successfulTransferMovesMoneyAndWritesTwoLedgerEntries() {
        Account from = account(1, "500.00");
        Account to = account(2, "100.00");
        givenAccounts(from, to);

        BankTransaction tx = service.process(transfer("200.00", 1L, 2L));

        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(new BigDecimal("300.00"), from.getBalance());
        assertEquals(new BigDecimal("300.00"), to.getBalance());

        // ArgumentCaptor: grab what was passed to ledger.save so we can inspect it
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger, times(2)).save(captor.capture());
        List<LedgerEntry> entries = captor.getAllValues();
        assertEquals(EntryDirection.DEBIT, entries.get(0).getDirection());
        assertEquals(from, entries.get(0).getAccount());
        assertEquals(EntryDirection.CREDIT, entries.get(1).getDirection());
        assertEquals(to, entries.get(1).getAccount());
    }

    @Test
    void depositCreditsTheAccountAndWritesOneLedgerEntry() {
        Account to = account(1, "0.00");
        givenAccounts(to);

        BankTransaction tx = service.process(
                new TransactionRequest(TransactionType.DEPOSIT, new BigDecimal("75.25"), null, 1L));

        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(new BigDecimal("75.25"), to.getBalance());
        verify(ledger, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    void withdrawalDebitsTheAccount() {
        Account from = account(1, "100.00");
        givenAccounts(from);

        BankTransaction tx = service.process(
                new TransactionRequest(TransactionType.WITHDRAWAL, new BigDecimal("40.00"), 1L, null));

        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(new BigDecimal("60.00"), from.getBalance());
    }

    // ---------- rejection paths: recorded as REJECTED, no money moves, no ledger entries ----------

    @Test
    void insufficientFundsIsRejectedAndMovesNothing() {
        Account from = account(1, "50.00");
        Account to = account(2, "0.00");
        givenAccounts(from, to);

        BankTransaction tx = service.process(transfer("100.00", 1L, 2L));

        assertEquals(TransactionStatus.REJECTED, tx.getStatus());
        assertEquals("Insufficient funds", tx.getRejectionReason());
        assertEquals(new BigDecimal("50.00"), from.getBalance());
        assertEquals(new BigDecimal("0.00"), to.getBalance());
        verify(ledger, never()).save(any());
    }

    @Test
    void inactiveAccountIsRejected() {
        Account from = account(1, "500.00");
        Account to = account(2, "0.00");
        to.setStatus(AccountStatus.FROZEN);
        givenAccounts(from, to);

        BankTransaction tx = service.process(transfer("10.00", 1L, 2L));

        assertEquals(TransactionStatus.REJECTED, tx.getStatus());
        assertEquals("Account 2 is FROZEN", tx.getRejectionReason());
        verify(ledger, never()).save(any());
    }

    @Test
    void sameAccountTransferIsRejected() {
        givenAccounts(account(1, "500.00"));

        BankTransaction tx = service.process(transfer("10.00", 1L, 1L));

        assertEquals(TransactionStatus.REJECTED, tx.getStatus());
        assertEquals("Source and destination accounts must differ", tx.getRejectionReason());
        verify(ledger, never()).save(any());
    }

    @Test
    void currencyMismatchIsRejected() {
        givenAccounts(account(1, "500.00", "INR"), account(2, "0.00", "USD"));

        BankTransaction tx = service.process(transfer("10.00", 1L, 2L));

        assertEquals(TransactionStatus.REJECTED, tx.getStatus());
        assertEquals("Currency mismatch: INR vs USD", tx.getRejectionReason());
        verify(ledger, never()).save(any());
    }

    // ---------- bad requests: exceptions, nothing saved at all ----------

    @Test
    void unknownAccountThrowsNotFoundAndSavesNothing() {
        when(accounts.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.process(
                new TransactionRequest(TransactionType.DEPOSIT, new BigDecimal("10.00"), null, 99L)));

        verify(transactions, never()).save(any());
        verify(ledger, never()).save(any());
    }

    @Test
    void depositWithoutDestinationThrowsInvalidRequest() {
        InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> service.process(
                new TransactionRequest(TransactionType.DEPOSIT, new BigDecimal("10.00"), null, null)));

        assertEquals("toAccountId is required for DEPOSIT", ex.getMessage());
        verifyNoInteractions(accounts, transactions, ledger);
    }

    // ---------- locking order ----------

    @Test
    void transferLocksTheLowerAccountIdFirst() {
        givenAccounts(account(1, "0.00"), account(2, "500.00"));

        service.process(transfer("10.00", 2L, 1L)); // from 2 to 1

        // Even though money flows 2 -> 1, account 1 must be locked first.
        InOrder order = inOrder(accounts);
        order.verify(accounts).findByIdForUpdate(1L);
        order.verify(accounts).findByIdForUpdate(2L);
    }
}
