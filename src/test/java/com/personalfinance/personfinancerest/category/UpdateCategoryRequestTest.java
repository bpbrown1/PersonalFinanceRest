package com.personalfinance.personfinancerest.category;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCategoryRequestTest {

    @Test
    void requiresAtLeastOneField() {
        assertThat(new UpdateCategoryRequest(null, null).isAnyFieldPresent()).isFalse();
        assertThat(new UpdateCategoryRequest("Groceries", null).isAnyFieldPresent()).isTrue();
        assertThat(new UpdateCategoryRequest(null, CategoryApplicability.EXPENSE).isAnyFieldPresent()).isTrue();
    }
}
