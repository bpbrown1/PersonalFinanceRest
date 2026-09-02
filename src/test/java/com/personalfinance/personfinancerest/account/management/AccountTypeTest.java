package com.personalfinance.personfinancerest.account.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTypeTest {

    @Test
    void derivesClassificationAndSupportedRateType() {
        assertThat(AccountType.CHECKING.classification()).isEqualTo(AccountClassification.ASSET);
        assertThat(AccountType.SAVINGS.classification()).isEqualTo(AccountClassification.ASSET);
        assertThat(AccountType.CASH.classification()).isEqualTo(AccountClassification.ASSET);
        assertThat(AccountType.CREDIT_CARD.classification()).isEqualTo(AccountClassification.LIABILITY);
        assertThat(AccountType.LOAN.classification()).isEqualTo(AccountClassification.LIABILITY);

        assertThat(AccountType.CHECKING.supports(InterestRateType.APY)).isTrue();
        assertThat(AccountType.CREDIT_CARD.supports(InterestRateType.APR)).isTrue();
        assertThat(AccountType.CASH.supports(InterestRateType.APY)).isFalse();
    }
}
