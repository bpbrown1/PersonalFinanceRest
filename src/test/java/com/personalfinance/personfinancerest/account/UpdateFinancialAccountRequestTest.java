package com.personalfinance.personfinancerest.account;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateFinancialAccountRequestTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsAnEmptyUpdate() {
        UpdateFinancialAccountRequest request = new UpdateFinancialAccountRequest(
                null, null, null, null, null
        );

        assertThat(invalidProperties(request)).containsExactly("anyFieldPresent");
    }

    @Test
    void rejectsInvalidOptionalFields() {
        UpdateFinancialAccountRequest request = new UpdateFinancialAccountRequest(
                " ",
                null,
                "US",
                null,
                new BigDecimal("1.234")
        );

        assertThat(invalidProperties(request))
                .containsExactlyInAnyOrder("name", "currency", "openingBalance");
    }

    @Test
    void acceptsAValidPartialUpdate() {
        UpdateFinancialAccountRequest request = new UpdateFinancialAccountRequest(
                "Primary Checking", null, null, null, null
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.changesFinancialTerms()).isFalse();
    }

    @Test
    void identifiesFinancialTermChanges() {
        UpdateFinancialAccountRequest request = new UpdateFinancialAccountRequest(
                null, null, "USD", null, null
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.changesFinancialTerms()).isTrue();
    }

    private Set<String> invalidProperties(UpdateFinancialAccountRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
