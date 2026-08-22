package com.personalfinance.personfinancerest.account;

import com.personalfinance.personfinancerest.shared.money.MoneyValues;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class FinancialAccountService {

    private final FinancialAccountRepository repository;
    private final CurrentUserProvider currentUserProvider;

    FinancialAccountService(FinancialAccountRepository repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    FinancialAccountResponse create(CreateFinancialAccountRequest request) {
        FinancialAccount account = new FinancialAccount(
                UUID.randomUUID(),
                currentUserProvider.userId(),
                request.name().trim(),
                request.type(),
                MoneyValues.currencyCode(request.currency()),
                request.openingDate(),
                MoneyValues.amountOrZero(request.openingBalance())
        );

        return FinancialAccountResponse.from(repository.saveAndFlush(account));
    }

    @Transactional(readOnly = true)
    List<FinancialAccountResponse> findAll() {
        return repository.findAllByOwnerIdOrderByCreatedAtAsc(currentUserProvider.userId()).stream()
                .map(FinancialAccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    FinancialAccountResponse findById(UUID accountId) {
        return repository.findByIdAndOwnerId(accountId, currentUserProvider.userId())
                .map(FinancialAccountResponse::from)
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
    }
}
