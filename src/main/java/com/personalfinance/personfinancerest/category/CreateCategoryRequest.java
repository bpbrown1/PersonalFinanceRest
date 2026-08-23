package com.personalfinance.personfinancerest.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull CategoryApplicability applicability,
        UUID parentId
) {
    public CreateCategoryRequest(String name, CategoryApplicability applicability) {
        this(name, applicability, null);
    }
}
