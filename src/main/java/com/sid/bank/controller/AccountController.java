package com.sid.bank.controller;

import com.sid.bank.dto.AccountResponse;
import com.sid.bank.dto.CreateAccountRequest;
import com.sid.bank.dto.TransactionResponse;
import com.sid.bank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse body = AccountResponse.from(accountService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return AccountResponse.from(accountService.get(id));
    }

    @GetMapping("/{id}/transactions")
    public List<TransactionResponse> history(@PathVariable Long id) {
        return accountService.history(id).stream().map(TransactionResponse::from).toList();
    }
}
