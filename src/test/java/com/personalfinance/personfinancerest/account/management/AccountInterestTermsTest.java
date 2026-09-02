package com.personalfinance.personfinancerest.account.management;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountInterestTermsTest {

    @Test
    void normalizesPercentagePointsToSixFractionalDigits() {
        assertThat(AccountInterestTerms.validateAndNormalize(
                AccountType.SAVINGS, new BigDecimal("4.25"), InterestRateType.APY
        )).isEqualByComparingTo("4.250000");
    }

    @Test
    void rejectsAnAprForAnAssetAccount() {
        assertThatThrownBy(() -> AccountInterestTerms.validateAndNormalize(
                AccountType.CHECKING, new BigDecimal("4.25"), InterestRateType.APR
        )).isInstanceOf(InvalidFinancialAccountRequestException.class);
    }

    @Test
    void rejectsAnUnpairedRate() {
        assertThatThrownBy(() -> AccountInterestTerms.validateAndNormalize(
                AccountType.LOAN, new BigDecimal("7.5"), null
        )).isInstanceOf(InvalidFinancialAccountRequestException.class);
    }
}
