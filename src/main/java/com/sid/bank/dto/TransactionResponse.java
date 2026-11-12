package com.sid.bank.dto;

import com.sid.bank.entity.BankTransaction;
import com.sid.bank.entity.TransactionStatus;
import com.sid.bank.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        Long fromAccountId,
        Long toAccountId,
        String rejectionReason,
        Instant createdAt
) {
    public static TransactionResponse from(BankTransaction t) {
        Long from = t.getFromAccount() == null ? null : t.getFromAccount().getId();
        Long to = t.getToAccount() == null ? null : t.getToAccount().getId();
        return new TransactionResponse(t.getId(), t.getType(), t.getStatus(), t.getAmount(), t.getCurrency(),
                from, to, t.getRejectionReason(), t.getCreatedAt());
    }
}
