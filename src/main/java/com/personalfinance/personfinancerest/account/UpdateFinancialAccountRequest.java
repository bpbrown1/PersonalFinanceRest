package com.personalfinance.personfinancerest.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFinancialAccountRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String name,
        AccountType type,
        @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter currency code") String currency,
        LocalDate openingDate,
        @Digits(integer = 17, fraction = 2) BigDecimal openingBalance
) {

    @JsonIgnore
    @AssertTrue(message = "must include at least one field to update")
    public boolean isAnyFieldPresent() {
        return name != null || type != null || currency != null || openingDate != null || openingBalance != null;
    }

    boolean changesFinancialTerms() {
        return currency != null || openingDate != null || openingBalance != null;
    }
}
