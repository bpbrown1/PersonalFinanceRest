package com.personalfinance.personfinancerest.category;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
class TransactionCategoryController {

    private final TransactionCategoryService service;

    TransactionCategoryController(TransactionCategoryService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + response.id())).body(response);
    }

    @GetMapping
    List<CategoryResponse> findAll(@RequestParam(defaultValue = "active") String status) {
        return service.findAll(CategoryStatusFilter.fromValue(status));
    }

    @GetMapping("/{categoryId}")
    CategoryResponse findById(@PathVariable UUID categoryId) {
        return service.findById(categoryId);
    }

    @PatchMapping("/{categoryId}")
    CategoryResponse update(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return service.update(categoryId, request);
    }

    @PatchMapping("/{categoryId}/parent")
    CategoryResponse updateParent(@PathVariable UUID categoryId,
                                  @RequestBody UpdateCategoryParentRequest request) {
        return service.updateParent(categoryId, request);
    }

    @PostMapping("/{categoryId}/archive")
    CategoryResponse archive(@PathVariable UUID categoryId) {
        return service.archive(categoryId);
    }

    @PostMapping("/{categoryId}/restore")
    CategoryResponse restore(@PathVariable UUID categoryId) {
        return service.restore(categoryId);
    }
}
