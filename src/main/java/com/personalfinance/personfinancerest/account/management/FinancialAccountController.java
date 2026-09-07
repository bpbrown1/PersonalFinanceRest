package com.personalfinance.personfinancerest.account.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
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
    List<FinancialAccountResponse> findAll(@RequestParam(defaultValue = "active") String status) {
        return service.findAll(AccountStatusFilter.fromValue(status));
    }

    @GetMapping("/{accountId}")
    FinancialAccountResponse findById(@PathVariable UUID accountId) {
        return service.findById(accountId);
    }

    @GetMapping("/name-matches")
    List<FinancialAccountResponse> findActiveNameMatches(
            @RequestParam @NotBlank @Size(max = 100) String name
    ) {
        return service.findActiveNameMatches(name);
    }

    @PatchMapping("/{accountId}")
    FinancialAccountResponse update(@PathVariable UUID accountId,
                                    @Valid @RequestBody UpdateFinancialAccountRequest request) {
        return service.update(accountId, request);
    }

    @PostMapping("/{accountId}/archive")
    FinancialAccountResponse archive(@PathVariable UUID accountId) {
        return service.archive(accountId);
    }

    @PostMapping("/{accountId}/restore")
    FinancialAccountResponse restore(@PathVariable UUID accountId) {
        return service.restore(accountId);
    }
}
