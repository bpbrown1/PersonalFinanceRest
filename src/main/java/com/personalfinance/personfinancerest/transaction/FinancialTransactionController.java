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
import java.math.BigDecimal;
import java.time.LocalDate;
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
    TransactionPageResponse findAll(
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return service.search(TransactionSearchCriteria.from(
                status, accountId, from, to, categoryId, type, minAmount, maxAmount,
                text, page, size, sort, direction
        ));
    }

    @GetMapping("/summary")
    List<TransactionSummaryResponse> summarize(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type
    ) {
        return service.summarize(from, to, accountId, categoryId, type);
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
