package com.personalfinance.personfinancerest.transaction;

public class InvalidTransactionStatusException extends RuntimeException {

    public InvalidTransactionStatusException(String status) {
        super("must be one of: active, deleted, all; received: " + status);
    }
}
