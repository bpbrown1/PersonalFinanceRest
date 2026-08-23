package com.personalfinance.personfinancerest.account.balance;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
class AccountBalanceController {

    private final AccountBalanceService service;

    AccountBalanceController(AccountBalanceService service) {
        this.service = service;
    }

    @PostMapping("/balance-snapshots")
    ResponseEntity<BalanceSnapshotResponse> create(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateBalanceSnapshotRequest request) {
        BalanceSnapshotResponse response = service.create(accountId, request);
        return ResponseEntity.created(URI.create(
                "/api/v1/accounts/" + accountId + "/balance-snapshots/" + response.id()
        )).body(response);
    }

    @GetMapping("/balance-snapshots")
    List<BalanceSnapshotResponse> findAll(@PathVariable UUID accountId) {
        return service.findAll(accountId);
    }

    @GetMapping("/balance-snapshots/{snapshotId}")
    BalanceSnapshotResponse findById(@PathVariable UUID accountId, @PathVariable UUID snapshotId) {
        return service.findById(accountId, snapshotId);
    }

    @GetMapping("/balance")
    AccountBalanceResponse findAsOf(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        return service.findAsOf(accountId, asOf == null ? Instant.now() : asOf);
    }
}
