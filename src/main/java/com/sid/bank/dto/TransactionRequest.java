package com.sid.bank.dto;

import com.sid.bank.entity.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// DEPOSIT needs toAccountId, WITHDRAWAL needs fromAccountId, TRANSFER needs both.
public record TransactionRequest(
        @NotNull TransactionType type,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        Long fromAccountId,
        Long toAccountId
) {
}
