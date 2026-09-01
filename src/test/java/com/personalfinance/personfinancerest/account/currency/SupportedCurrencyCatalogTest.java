package com.personalfinance.personfinancerest.account.currency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedCurrencyCatalogTest {

    @Test
    void loadsTheCuratedCurrentCurrencySnapshotInStableOrder() {
        assertThat(SupportedCurrencyCatalog.codes())
                .hasSize(155)
                .isSorted()
                .contains("EUR", "USD", "XAF", "XCG", "ZWG")
                .doesNotContain("BGN", "XAU", "XTS", "XXX", "USN");
        assertThat(SupportedCurrencyCatalog.SOURCE_PUBLISHED_DATE).isEqualTo("2026-01-01");
    }

    @Test
    void matchesSupportedCodesCaseInsensitivelyWithoutAcceptingMalformedValues() {
        assertThat(SupportedCurrencyCatalog.supports("usd")).isTrue();
        assertThat(SupportedCurrencyCatalog.supports("BGN")).isFalse();
        assertThat(SupportedCurrencyCatalog.supports("ZZZ")).isFalse();
        assertThat(SupportedCurrencyCatalog.supports(" USD ")).isFalse();
        assertThat(SupportedCurrencyCatalog.supports(null)).isFalse();
    }
}
