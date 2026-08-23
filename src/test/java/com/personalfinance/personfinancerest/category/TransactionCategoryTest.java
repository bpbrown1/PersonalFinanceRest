package com.personalfinance.personfinancerest.category;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCategoryTest {

    @Test
    void normalizesNamesAndManagesTheActiveNameKeyAcrossLifecycleChanges() {
        TransactionCategory category = new TransactionCategory(
                UUID.randomUUID(), UUID.randomUUID(), "  Food   and Dining  ", CategoryApplicability.EXPENSE
        );

        assertThat(category.getName()).isEqualTo("Food and Dining");
        assertThat(category.getNormalizedName()).isEqualTo("food and dining");
        assertThat(category.getStatus()).isEqualTo(CategoryStatus.ACTIVE);

        Instant archivedAt = Instant.parse("2026-08-23T12:00:00Z");
        category.archive(archivedAt);
        category.archive(archivedAt.plusSeconds(60));
        assertThat(category.getArchivedAt()).isEqualTo(archivedAt);
        assertThat(category.getStatus()).isEqualTo(CategoryStatus.ARCHIVED);

        category.update("  FOOD   &   DRINK  ", CategoryApplicability.BOTH);
        assertThat(category.getName()).isEqualTo("FOOD & DRINK");
        assertThat(category.getNormalizedName()).isEqualTo("food & drink");
        assertThat(category.getApplicability()).isEqualTo(CategoryApplicability.BOTH);

        category.restore();
        assertThat(category.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(category.getArchivedAt()).isNull();
    }

    @Test
    void assignsAndClearsAParent() {
        TransactionCategory category = new TransactionCategory(
                UUID.randomUUID(), UUID.randomUUID(), "Dining", CategoryApplicability.EXPENSE
        );
        UUID parentId = UUID.randomUUID();

        category.assignParent(parentId);
        assertThat(category.getParentId()).isEqualTo(parentId);

        category.assignParent(null);
        assertThat(category.getParentId()).isNull();
    }
}
