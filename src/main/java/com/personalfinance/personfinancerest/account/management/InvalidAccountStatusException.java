package com.personalfinance.personfinancerest.account.management;

public class InvalidAccountStatusException extends RuntimeException {

    public InvalidAccountStatusException(String status) {
        super("must be one of: active, archived, all; received: " + status);
    }
}
