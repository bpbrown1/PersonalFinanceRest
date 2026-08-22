package com.personalfinance.personfinancerest.account;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
class FinancialAccountController {

    private final FinancialAccountService service;

    FinancialAccountController(FinancialAccountService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<FinancialAccountResponse> create(@Valid @RequestBody CreateFinancialAccountRequest request) {
        FinancialAccountResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + response.id())).body(response);
    }

    @GetMapping
    List<FinancialAccountResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{accountId}")
    FinancialAccountResponse findById(@PathVariable UUID accountId) {
        return service.findById(accountId);
    }
}
