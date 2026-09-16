package com.personalfinance.personfinancerest.account.reconciliation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/reconciliations")
class AccountReconciliationController {

    private final AccountReconciliationService service;

    AccountReconciliationController(AccountReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    ReconciliationPreviewResponse preview(
            @PathVariable UUID accountId,
            @Valid @RequestBody ReconciliationPreviewRequest request
    ) {
        return service.preview(accountId, request);
    }

    @PostMapping
    ResponseEntity<ReconciliationResponse> confirm(
            @PathVariable UUID accountId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReconciliationConfirmRequest request
    ) {
        ReconciliationResponse response = service.confirm(accountId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    ReconciliationPageResponse history(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return service.history(accountId, page, size);
    }
}
