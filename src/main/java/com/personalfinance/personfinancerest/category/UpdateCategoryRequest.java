package com.personalfinance.personfinancerest.category;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String name,
        CategoryApplicability applicability
) {

    @JsonIgnore
    @AssertTrue(message = "must include at least one field to update")
    public boolean isAnyFieldPresent() {
        return name != null || applicability != null;
    }
}
