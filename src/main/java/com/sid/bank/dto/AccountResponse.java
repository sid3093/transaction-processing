package com.sid.bank.dto;

import com.sid.bank.entity.Account;
import com.sid.bank.entity.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        String ownerName,
        String currency,
        AccountStatus status,
        BigDecimal balance,
        Instant createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getAccountNumber(), a.getOwnerName(), a.getCurrency(),
                a.getStatus(), a.getBalance(), a.getCreatedAt());
    }
}
