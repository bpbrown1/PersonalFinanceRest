package com.personalfinance.personfinancerest.category;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID categoryId) {
        super("Transaction category not found: " + categoryId);
    }
}
