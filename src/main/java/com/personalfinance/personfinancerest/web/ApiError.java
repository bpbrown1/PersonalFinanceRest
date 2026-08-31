package com.personalfinance.personfinancerest.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        Map<String, String> fieldErrors,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID existingBudgetId
) {
    public ApiError(Instant timestamp, int status, String error, Map<String, String> fieldErrors) {
        this(timestamp, status, error, fieldErrors, null);
    }
}
