package com.personalfinance.personfinancerest.budget;

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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
class BudgetController {

    private final BudgetService service;
    private final BudgetProgressService progressService;

    BudgetController(BudgetService service, BudgetProgressService progressService) {
        this.service = service;
        this.progressService = progressService;
    }

    @PostMapping
    ResponseEntity<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        BudgetResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + response.id())).body(response);
    }

    @GetMapping
    List<BudgetResponse> findAll(@RequestParam(defaultValue = "active") String status) {
        return service.findAll(BudgetStatusFilter.fromValue(status));
    }

    @GetMapping("/{budgetId}")
    BudgetResponse findById(@PathVariable UUID budgetId) {
        return service.findById(budgetId);
    }

    @GetMapping("/{budgetId}/progress")
    BudgetProgressResponse progress(@PathVariable UUID budgetId,
                                    @RequestParam(required = false) UUID accountId,
                                    @RequestParam(required = false) UUID categoryId) {
        return progressService.calculate(budgetId, accountId, categoryId);
    }

    @PutMapping("/{budgetId}")
    BudgetResponse update(@PathVariable UUID budgetId, @Valid @RequestBody UpdateBudgetRequest request) {
        return service.update(budgetId, request);
    }

    @PostMapping("/{budgetId}/archive")
    BudgetResponse archive(@PathVariable UUID budgetId) {
        return service.archive(budgetId);
    }

    @PostMapping("/{budgetId}/restore")
    BudgetResponse restore(@PathVariable UUID budgetId) {
        return service.restore(budgetId);
    }

    @PostMapping("/{budgetId}/lines")
    BudgetResponse addLine(@PathVariable UUID budgetId,
                           @Valid @RequestBody CreateBudgetLineRequest request) {
        return service.addLine(budgetId, request);
    }

    @PutMapping("/{budgetId}/lines/{lineId}")
    BudgetResponse updateLine(@PathVariable UUID budgetId, @PathVariable UUID lineId,
                              @Valid @RequestBody UpdateBudgetLineRequest request) {
        return service.updateLine(budgetId, lineId, request);
    }

    @PutMapping("/{budgetId}/lines/reorder")
    BudgetResponse reorderLines(@PathVariable UUID budgetId,
                                @Valid @RequestBody ReorderBudgetLinesRequest request) {
        return service.reorderLines(budgetId, request);
    }

    @PostMapping("/{budgetId}/lines/{lineId}/archive")
    BudgetResponse archiveLine(@PathVariable UUID budgetId, @PathVariable UUID lineId) {
        return service.archiveLine(budgetId, lineId);
    }

    @PostMapping("/{budgetId}/lines/{lineId}/restore")
    BudgetResponse restoreLine(@PathVariable UUID budgetId, @PathVariable UUID lineId) {
        return service.restoreLine(budgetId, lineId);
    }
}
