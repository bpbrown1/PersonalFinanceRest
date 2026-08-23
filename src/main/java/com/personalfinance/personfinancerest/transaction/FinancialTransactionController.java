package com.personalfinance.personfinancerest.transaction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
class FinancialTransactionController {

    private final FinancialTransactionService service;

    FinancialTransactionController(FinancialTransactionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + response.id())).body(response);
    }

    @GetMapping
    List<TransactionResponse> findAll(@RequestParam(defaultValue = "active") String status) {
        return service.findAll(TransactionStatusFilter.fromValue(status));
    }

    @GetMapping("/{transactionId}")
    TransactionResponse findById(@PathVariable UUID transactionId) {
        return service.findById(transactionId);
    }

    @PutMapping("/{transactionId}")
    TransactionResponse update(@PathVariable UUID transactionId,
                               @Valid @RequestBody UpdateTransactionRequest request) {
        return service.update(transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    TransactionResponse delete(@PathVariable UUID transactionId) {
        return service.delete(transactionId);
    }

    @PostMapping("/{transactionId}/restore")
    TransactionResponse restore(@PathVariable UUID transactionId) {
        return service.restore(transactionId);
    }
}
