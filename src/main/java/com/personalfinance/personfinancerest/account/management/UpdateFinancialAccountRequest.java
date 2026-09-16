package com.personalfinance.personfinancerest.account.management;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.personalfinance.personfinancerest.account.currency.SupportedCurrency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class UpdateFinancialAccountRequest {

    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
    @Size(max = 100)
    private String name;
    private AccountType type;
    @SupportedCurrency
    private String currency;
    private LocalDate openingDate;
    @Digits(integer = 17, fraction = 2)
    private BigDecimal openingBalance;
    @DecimalMin("0.000000")
    @DecimalMax("999.999999")
    @Digits(integer = 3, fraction = 6)
    private BigDecimal interestRate;
    private InterestRateType interestRateType;
    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
    @Size(max = 100)
    private String institutionName;
    @Pattern(regexp = "\\d{4}", message = "must contain exactly four digits")
    private String accountNumberLastFour;

    private boolean interestRateFieldPresent;
    private boolean interestRateTypeFieldPresent;
    private boolean institutionNameFieldPresent;
    private boolean accountNumberLastFourFieldPresent;

    public UpdateFinancialAccountRequest() {
    }

    public UpdateFinancialAccountRequest(String name, AccountType type, String currency,
                                         LocalDate openingDate, BigDecimal openingBalance) {
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.openingDate = openingDate;
        this.openingBalance = openingBalance;
    }

    public UpdateFinancialAccountRequest(String name, AccountType type, String currency,
                                         LocalDate openingDate, BigDecimal openingBalance,
                                         BigDecimal interestRate, InterestRateType interestRateType) {
        this(name, type, currency, openingDate, openingBalance);
        this.interestRate = interestRate;
        this.interestRateType = interestRateType;
        this.interestRateFieldPresent = true;
        this.interestRateTypeFieldPresent = true;
    }

    public String name() { return name; }

    public AccountType type() { return type; }

    public String currency() { return currency; }

    public LocalDate openingDate() { return openingDate; }

    public BigDecimal openingBalance() { return openingBalance; }

    public BigDecimal interestRate() { return interestRate; }

    public InterestRateType interestRateType() { return interestRateType; }

    public String institutionName() { return institutionName; }

    public String accountNumberLastFour() { return accountNumberLastFour; }

    public void setName(String name) { this.name = name; }

    public void setType(AccountType type) { this.type = type; }

    public void setCurrency(String currency) { this.currency = currency; }

    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }

    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
        this.interestRateFieldPresent = true;
    }

    public void setInterestRateType(InterestRateType interestRateType) {
        this.interestRateType = interestRateType;
        this.interestRateTypeFieldPresent = true;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
        this.institutionNameFieldPresent = true;
    }

    public void setAccountNumberLastFour(String accountNumberLastFour) {
        this.accountNumberLastFour = accountNumberLastFour;
        this.accountNumberLastFourFieldPresent = true;
    }

    @JsonIgnore
    @AssertTrue(message = "must include at least one field to update")
    public boolean isAnyFieldPresent() {
        return name != null || type != null || currency != null || openingDate != null || openingBalance != null
                || interestRateFieldPresent || interestRateTypeFieldPresent
                || institutionNameFieldPresent || accountNumberLastFourFieldPresent;
    }

    boolean hasInterestRateField() { return interestRateFieldPresent; }

    boolean hasInterestRateTypeField() { return interestRateTypeFieldPresent; }

    boolean hasInstitutionNameField() { return institutionNameFieldPresent; }

    boolean hasAccountNumberLastFourField() { return accountNumberLastFourFieldPresent; }

    boolean changesFinancialTerms() {
        return currency != null || openingDate != null || openingBalance != null;
    }
}
