package com.personalfinance.personfinancerest.budget;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateBudgetRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        List<@Valid CreateBudgetLineRequest> lines
) {
}
