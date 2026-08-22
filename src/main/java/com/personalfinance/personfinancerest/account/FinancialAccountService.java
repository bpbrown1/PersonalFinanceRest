package com.personalfinance.personfinancerest.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
class FinancialAccountService {

    static final UUID DEFAULT_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FinancialAccountRepository repository;

    FinancialAccountService(FinancialAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    FinancialAccountResponse create(CreateFinancialAccountRequest request) {
        BigDecimal openingBalance = request.openingBalance() == null
                ? BigDecimal.ZERO.setScale(2)
                : request.openingBalance().setScale(2);

        FinancialAccount account = new FinancialAccount(
                UUID.randomUUID(),
                DEFAULT_OWNER_ID,
                request.name().trim(),
                request.type(),
                request.currency().toUpperCase(Locale.ROOT),
                request.openingDate(),
                openingBalance
        );

        return FinancialAccountResponse.from(repository.saveAndFlush(account));
    }
}
