package com.personalfinance.personfinancerest.account.currency;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class SupportedCurrencyValidator implements ConstraintValidator<SupportedCurrency, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || SupportedCurrencyCatalog.supports(value);
    }
}
