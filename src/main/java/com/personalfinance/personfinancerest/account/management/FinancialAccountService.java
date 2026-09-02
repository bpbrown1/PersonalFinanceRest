package com.personalfinance.personfinancerest.account.management;

import com.personalfinance.personfinancerest.account.activity.FinancialAccountActivity;
import com.personalfinance.personfinancerest.shared.money.MoneyValues;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class FinancialAccountService {

    private final FinancialAccountRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final FinancialAccountActivity financialAccountActivity;
    private final OpeningBalanceHistory openingBalanceHistory;

    FinancialAccountService(FinancialAccountRepository repository, CurrentUserProvider currentUserProvider,
                            FinancialAccountActivity financialAccountActivity,
                            OpeningBalanceHistory openingBalanceHistory) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.financialAccountActivity = financialAccountActivity;
        this.openingBalanceHistory = openingBalanceHistory;
    }

    @Transactional
    FinancialAccountResponse create(CreateFinancialAccountRequest request) {
        BigDecimal interestRate = AccountInterestTerms.validateAndNormalize(
                request.type(), request.interestRate(), request.interestRateType()
        );
        FinancialAccount account = new FinancialAccount(
                UUID.randomUUID(),
                currentUserProvider.userId(),
                request.name().trim(),
                request.type(),
                MoneyValues.currencyCode(request.currency()),
                request.openingDate(),
                MoneyValues.amountOrZero(request.openingBalance()),
                interestRate,
                request.interestRateType()
        );

        FinancialAccount savedAccount = repository.saveAndFlush(account);
        openingBalanceHistory.createFor(savedAccount);
        return FinancialAccountResponse.from(savedAccount);
    }

    @Transactional(readOnly = true)
    List<FinancialAccountResponse> findAll(AccountStatusFilter status) {
        UUID ownerId = currentUserProvider.userId();
        List<FinancialAccount> accounts = switch (status) {
            case ACTIVE -> repository.findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAsc(ownerId);
            case ARCHIVED -> repository.findAllByOwnerIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(ownerId);
            case ALL -> repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId);
        };

        return accounts.stream()
                .map(FinancialAccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    FinancialAccountResponse findById(UUID accountId) {
        return repository.findByIdAndOwnerId(accountId, currentUserProvider.userId())
                .map(FinancialAccountResponse::from)
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
    }

    @Transactional
    FinancialAccountResponse update(UUID accountId, UpdateFinancialAccountRequest request) {
        FinancialAccount account = findOwnedAccount(accountId);
        if (request.changesFinancialTerms() && financialAccountActivity.existsFor(accountId)) {
            throw new FinancialAccountInUseException(accountId);
        }

        if (request.hasInterestRateField() != request.hasInterestRateTypeField()) {
            throw new InvalidFinancialAccountRequestException(java.util.Map.of(
                    "interestRate", "must be updated together with interestRateType",
                    "interestRateType", "must be updated together with interestRate"
            ));
        }
        AccountType type = request.type() == null ? account.getType() : request.type();
        BigDecimal interestRate = request.hasInterestRateField()
                ? request.interestRate() : account.getInterestRate();
        InterestRateType interestRateType = request.hasInterestRateTypeField()
                ? request.interestRateType() : account.getInterestRateType();
        interestRate = AccountInterestTerms.validateAndNormalize(type, interestRate, interestRateType);

        account.update(
                request.name() == null ? account.getName() : request.name().trim(),
                type,
                request.currency() == null ? account.getCurrency() : MoneyValues.currencyCode(request.currency()),
                request.openingDate() == null ? account.getOpeningDate() : request.openingDate(),
                request.openingBalance() == null
                        ? account.getOpeningBalance()
                        : MoneyValues.amountOrZero(request.openingBalance()),
                interestRate,
                interestRateType
        );
        if (request.openingBalance() != null) {
            account.recordCurrentBalance(account.getOpeningBalance());
        }
        if (request.openingDate() != null || request.openingBalance() != null) {
            openingBalanceHistory.updateFor(account);
        }
        return FinancialAccountResponse.from(repository.saveAndFlush(account));
    }

    @Transactional
    FinancialAccountResponse archive(UUID accountId) {
        FinancialAccount account = findOwnedAccount(accountId);
        account.archive(Instant.now());
        return FinancialAccountResponse.from(repository.saveAndFlush(account));
    }

    @Transactional
    FinancialAccountResponse restore(UUID accountId) {
        FinancialAccount account = findOwnedAccount(accountId);
        account.restore();
        return FinancialAccountResponse.from(repository.saveAndFlush(account));
    }

    private FinancialAccount findOwnedAccount(UUID accountId) {
        return repository.findByIdAndOwnerId(accountId, currentUserProvider.userId())
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
    }
}
