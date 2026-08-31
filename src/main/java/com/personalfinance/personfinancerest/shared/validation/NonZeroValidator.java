package com.personalfinance.personfinancerest.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class NonZeroValidator implements ConstraintValidator<NonZero, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || value.signum() != 0;
    }
}
