package com.personalfinance.personfinancerest.account.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNamesTest {

    @Test
    void trimsDisplayNamesAndNormalizesCaseForMatching() {
        assertThat(AccountNames.displayName(" Everyday Checking ")).isEqualTo("Everyday Checking");
        assertThat(AccountNames.normalizedName(" Everyday CHECKING ")).isEqualTo("everyday checking");
    }

    @Test
    void trimsOptionalInstitutionNamesWithoutInventingAValue() {
        assertThat(AccountNames.optionalDisplayName(" Example Bank ")).isEqualTo("Example Bank");
        assertThat(AccountNames.optionalDisplayName(null)).isNull();
    }
}
