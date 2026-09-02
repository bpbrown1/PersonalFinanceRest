package com.personalfinance.personfinancerest.account.management;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

final class AccountInterestTerms {

    private static final BigDecimal MAX_RATE = new BigDecimal("999.999999");

    private AccountInterestTerms() {
    }

    static BigDecimal validateAndNormalize(
            AccountType accountType,
            BigDecimal interestRate,
            InterestRateType interestRateType
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        if ((interestRate == null) != (interestRateType == null)) {
            errors.put("interestRate", "must be supplied together with interestRateType");
            errors.put("interestRateType", "must be supplied together with interestRate");
        } else if (interestRate != null) {
            if (interestRate.signum() < 0 || interestRate.compareTo(MAX_RATE) > 0) {
                errors.put("interestRate", "must be between 0.000000 and 999.999999");
            } else if (interestRate.scale() > 6) {
                errors.put("interestRate", "must have no more than 6 fractional digits");
            }
            if (!accountType.supports(interestRateType)) {
                errors.put("interestRateType", accountType.interestTermsMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidFinancialAccountRequestException(errors);
        }
        return interestRate == null ? null : interestRate.setScale(6, RoundingMode.UNNECESSARY);
    }
}
