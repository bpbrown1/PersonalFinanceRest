package com.personalfinance.personfinancerest.category;

public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String name) {
        super("An active transaction category already uses the name: " + name);
    }
}
