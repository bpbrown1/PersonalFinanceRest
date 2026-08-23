package com.personalfinance.personfinancerest.account.management;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialAccountTest {

    @Test
    void newAccountIsActive() {
        FinancialAccount account = account();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getArchivedAt()).isNull();
    }

    @Test
    void archivesIdempotentlyAndPreservesTheOriginalTimestamp() {
        FinancialAccount account = account();
        Instant firstArchiveTime = Instant.parse("2026-08-22T12:00:00Z");

        account.archive(firstArchiveTime);
        account.archive(Instant.parse("2026-08-23T12:00:00Z"));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
        assertThat(account.getArchivedAt()).isEqualTo(firstArchiveTime);
    }

    @Test
    void restoresAnArchivedAccount() {
        FinancialAccount account = account();
        account.archive(Instant.parse("2026-08-22T12:00:00Z"));

        account.restore();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getArchivedAt()).isNull();
    }

    @Test
    void updatesAccountFields() {
        FinancialAccount account = account();

        account.update(
                "Emergency Savings",
                AccountType.SAVINGS,
                "EUR",
                LocalDate.of(2026, 8, 1),
                new BigDecimal("1500.50")
        );

        assertThat(account.getName()).isEqualTo("Emergency Savings");
        assertThat(account.getType()).isEqualTo(AccountType.SAVINGS);
        assertThat(account.getCurrency()).isEqualTo("EUR");
        assertThat(account.getOpeningDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("1500.50");
    }

    private FinancialAccount account() {
        return new FinancialAccount(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Everyday Checking",
                AccountType.CHECKING,
                "USD",
                LocalDate.of(2026, 8, 20),
                new BigDecimal("1250.75")
        );
    }
}
