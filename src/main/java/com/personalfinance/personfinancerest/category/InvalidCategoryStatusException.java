package com.personalfinance.personfinancerest.category;

public class InvalidCategoryStatusException extends RuntimeException {

    public InvalidCategoryStatusException(String status) {
        super("must be one of: active, archived, all; received: " + status);
    }
}
