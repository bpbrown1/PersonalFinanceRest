package com.personalfinance.personfinancerest.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal sourceAmount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal destinationAmount,
        @NotNull LocalDate transactionDate,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 2000) String notes,
        @Size(max = 255) String externalReference
) {
}
