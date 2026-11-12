package com.sid.bank.controller;

import com.sid.bank.dto.TransactionRequest;
import com.sid.bank.dto.TransactionResponse;
import com.sid.bank.entity.BankTransaction;
import com.sid.bank.entity.TransactionStatus;
import com.sid.bank.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // 201 Created if it went through, 422 Unprocessable if a business rule rejected it.
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        BankTransaction tx = transactionService.process(request);
        int status = tx.getStatus() == TransactionStatus.COMPLETED ? 201 : 422;
        return ResponseEntity.status(status).body(TransactionResponse.from(tx));
    }
}
