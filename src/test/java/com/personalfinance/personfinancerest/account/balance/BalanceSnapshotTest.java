package com.personalfinance.personfinancerest.account.balance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceSnapshotTest {

    @Test
    void updatesOnlyTheOpeningSnapshot() {
        BalanceSnapshot snapshot = snapshot(BalanceSnapshotSource.OPENING);
        Instant effectiveAt = Instant.parse("2026-08-01T00:00:00Z");

        snapshot.updateOpeningValues(new BigDecimal("1500.50"), effectiveAt);

        assertThat(snapshot.getBalance()).isEqualByComparingTo("1500.50");
        assertThat(snapshot.getEffectiveAt()).isEqualTo(effectiveAt);
    }

    @Test
    void manualSnapshotsAreAppendOnly() {
        BalanceSnapshot snapshot = snapshot(BalanceSnapshotSource.MANUAL);

        assertThatThrownBy(() -> snapshot.updateOpeningValues(
                new BigDecimal("1500.50"),
                Instant.parse("2026-08-01T00:00:00Z")
        )).isInstanceOf(IllegalStateException.class);
    }

    private BalanceSnapshot snapshot(BalanceSnapshotSource source) {
        return new BalanceSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1250.75"),
                Instant.parse("2026-08-20T00:00:00Z"),
                source
        );
    }
}
