package com.personalfinance.personfinancerest.recurringexpense;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurring-expenses")
class RecurringExpenseController {

    private final RecurringExpenseService service;

    RecurringExpenseController(RecurringExpenseService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<RecurringExpenseResponse> create(@Valid @RequestBody CreateRecurringExpenseRequest request) {
        RecurringExpenseResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/recurring-expenses/" + response.id())).body(response);
    }

    @GetMapping
    List<RecurringExpenseResponse> findAll(@RequestParam(defaultValue = "active") String status) {
        return service.findAll(RecurringExpenseStatusFilter.fromValue(status));
    }

    @GetMapping("/occurrences")
    List<RecurringExpenseOccurrenceResponse> occurrences(@RequestParam LocalDate from,
                                                         @RequestParam LocalDate to) {
        return service.occurrences(from, to);
    }

    @GetMapping("/{id}")
    RecurringExpenseResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    RecurringExpenseResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateRecurringExpenseRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    RecurringExpenseResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/restore")
    RecurringExpenseResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
