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
@RequestMapping("/api/v1/transfers")
class TransferController {

    private final TransferService service;

    TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest request) {
        TransferResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + response.id())).body(response);
    }

    @GetMapping
    List<TransferResponse> findAll(@RequestParam(defaultValue = "active") String status) {
        return service.findAll(TransactionStatusFilter.fromValue(status));
    }

    @GetMapping("/{transferId}")
    TransferResponse findById(@PathVariable UUID transferId) {
        return service.findById(transferId);
    }

    @PutMapping("/{transferId}")
    TransferResponse update(@PathVariable UUID transferId,
                            @Valid @RequestBody UpdateTransferRequest request) {
        return service.update(transferId, request);
    }

    @DeleteMapping("/{transferId}")
    TransferResponse delete(@PathVariable UUID transferId) {
        return service.delete(transferId);
    }

    @PostMapping("/{transferId}/restore")
    TransferResponse restore(@PathVariable UUID transferId) {
        return service.restore(transferId);
    }
}
