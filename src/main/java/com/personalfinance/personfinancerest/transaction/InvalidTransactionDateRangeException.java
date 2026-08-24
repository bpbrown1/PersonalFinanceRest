package com.personalfinance.personfinancerest.transaction;

public class InvalidTransactionDateRangeException extends RuntimeException {

    public InvalidTransactionDateRangeException() {
        super("from must be on or before to");
    }
}
