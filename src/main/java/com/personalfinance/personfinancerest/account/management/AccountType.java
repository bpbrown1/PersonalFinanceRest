package com.personalfinance.personfinancerest.account.management;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AccountType {
    CHECKING(AccountClassification.ASSET, InterestRateType.APY),
    SAVINGS(AccountClassification.ASSET, InterestRateType.APY),
    CASH(AccountClassification.ASSET, null),
    CREDIT_CARD(AccountClassification.LIABILITY, InterestRateType.APR),
    LOAN(AccountClassification.LIABILITY, InterestRateType.APR);

    private final AccountClassification classification;
    private final InterestRateType supportedInterestRateType;

    AccountType(AccountClassification classification, InterestRateType supportedInterestRateType) {
        this.classification = classification;
        this.supportedInterestRateType = supportedInterestRateType;
    }

    @JsonCreator
    public static AccountType fromValue(String value) {
        return AccountType.valueOf(value.trim().replace(' ', '_').toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public AccountClassification classification() {
        return classification;
    }

    boolean supports(InterestRateType interestRateType) {
        return supportedInterestRateType == interestRateType;
    }

    String interestTermsMessage() {
        return supportedInterestRateType == null
                ? "is not supported for cash accounts"
                : "must be " + supportedInterestRateType.name() + " for " + toValue() + " accounts";
    }
}
