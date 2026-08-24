package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.account.management.AccountStatus;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.CategoryApplicability;
import com.personalfinance.personfinancerest.category.CategoryNotFoundException;
import com.personalfinance.personfinancerest.category.CategoryStatus;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.shared.money.MoneyValues;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
class FinancialTransactionService {

    private final FinancialTransactionRepository repository;
    private final FinancialAccountRepository accountRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;

    FinancialTransactionService(FinancialTransactionRepository repository,
                                FinancialAccountRepository accountRepository,
                                TransactionCategoryRepository categoryRepository,
                                CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    TransactionResponse create(CreateTransactionRequest request) {
        ensureStandaloneType(request.type());
        UUID ownerId = currentUserProvider.userId();
        FinancialAccount account = lockOwnedAccounts(ownerId, Set.of(request.accountId())).get(request.accountId());
        ensureActiveAccount(account);
        validateDate(request.transactionDate(), account);
        validateCategory(request.categoryId(), ownerId, request.type(), true);

        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), ownerId, request.accountId(), request.categoryId(),
                MoneyValues.amountOrZero(request.amount()), request.type(), request.transactionDate(),
                request.description(), request.merchantPayee(), request.notes(), request.externalReference()
        );
        FinancialTransaction saved = repository.saveAndFlush(transaction);
        account.applyBalanceDelta(saved.balanceImpact());
        accountRepository.saveAndFlush(account);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    List<TransactionResponse> findAll(TransactionStatusFilter status) {
        UUID ownerId = currentUserProvider.userId();
        List<FinancialTransaction> transactions = switch (status) {
            case ACTIVE -> repository
                    .findAllByOwnerIdAndDeletedAtIsNullOrderByTransactionDateDescCreatedAtDesc(ownerId);
            case DELETED -> repository
                    .findAllByOwnerIdAndDeletedAtIsNotNullOrderByTransactionDateDescCreatedAtDesc(ownerId);
            case ALL -> repository.findAllByOwnerIdOrderByTransactionDateDescCreatedAtDesc(ownerId);
        };
        return transactions.stream().map(TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    TransactionResponse findById(UUID transactionId) {
        return TransactionResponse.from(findOwnedTransaction(transactionId));
    }

    @Transactional(readOnly = true)
    List<TransactionSummaryResponse> summarize(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransactionDateRangeException();
        }
        return repository.summarize(
                        currentUserProvider.userId(), from, to,
                        TransactionType.INCOME, TransactionType.EXPENSE
                ).stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }

    @Transactional
    TransactionResponse update(UUID transactionId, UpdateTransactionRequest request) {
        UUID ownerId = currentUserProvider.userId();
        FinancialTransaction transaction = findOwnedTransaction(transactionId);
        ensureStandaloneTransaction(transaction);
        ensureStandaloneType(request.type());
        UUID oldAccountId = transaction.getAccountId();
        Map<UUID, FinancialAccount> accounts = lockOwnedAccounts(
                ownerId, List.of(oldAccountId, request.accountId())
        );
        FinancialAccount newAccount = accounts.get(request.accountId());
        if (!oldAccountId.equals(request.accountId())) {
            ensureActiveAccount(newAccount);
        }
        validateDate(request.transactionDate(), newAccount);

        boolean categoryChanged = !Objects.equals(transaction.getCategoryId(), request.categoryId());
        validateCategory(request.categoryId(), ownerId, request.type(), categoryChanged);

        BigDecimal oldImpact = transaction.getStatus() == TransactionStatus.ACTIVE
                ? transaction.balanceImpact() : BigDecimal.ZERO;
        transaction.replace(
                request.accountId(), request.categoryId(), MoneyValues.amountOrZero(request.amount()),
                request.type(), request.transactionDate(), request.description(), request.merchantPayee(),
                request.notes(), request.externalReference()
        );
        BigDecimal newImpact = transaction.getStatus() == TransactionStatus.ACTIVE
                ? transaction.balanceImpact() : BigDecimal.ZERO;

        if (oldAccountId.equals(request.accountId())) {
            newAccount.applyBalanceDelta(newImpact.subtract(oldImpact));
        } else {
            accounts.get(oldAccountId).applyBalanceDelta(oldImpact.negate());
            newAccount.applyBalanceDelta(newImpact);
        }

        FinancialTransaction saved = repository.saveAndFlush(transaction);
        accountRepository.saveAllAndFlush(accounts.values());
        return TransactionResponse.from(saved);
    }

    @Transactional
    TransactionResponse delete(UUID transactionId) {
        UUID ownerId = currentUserProvider.userId();
        FinancialTransaction transaction = findOwnedTransaction(transactionId);
        ensureStandaloneTransaction(transaction);
        if (transaction.getStatus() == TransactionStatus.ACTIVE) {
            FinancialAccount account = lockOwnedAccounts(ownerId, Set.of(transaction.getAccountId()))
                    .get(transaction.getAccountId());
            account.applyBalanceDelta(transaction.balanceImpact().negate());
            accountRepository.saveAndFlush(account);
            transaction.softDelete(Instant.now());
        }
        return TransactionResponse.from(repository.saveAndFlush(transaction));
    }

    @Transactional
    TransactionResponse restore(UUID transactionId) {
        UUID ownerId = currentUserProvider.userId();
        FinancialTransaction transaction = findOwnedTransaction(transactionId);
        ensureStandaloneTransaction(transaction);
        if (transaction.getStatus() == TransactionStatus.DELETED) {
            FinancialAccount account = lockOwnedAccounts(ownerId, Set.of(transaction.getAccountId()))
                    .get(transaction.getAccountId());
            ensureActiveAccount(account);
            validateDate(transaction.getTransactionDate(), account);
            validateCategory(transaction.getCategoryId(), ownerId, transaction.getType(), false);
            account.applyBalanceDelta(transaction.balanceImpact());
            accountRepository.saveAndFlush(account);
            transaction.restore();
        }
        return TransactionResponse.from(repository.saveAndFlush(transaction));
    }

    private FinancialTransaction findOwnedTransaction(UUID transactionId) {
        return repository.findByIdAndOwnerId(transactionId, currentUserProvider.userId())
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private void ensureStandaloneType(TransactionType type) {
        if (type.isTransfer()) {
            throw new TransactionConflictException("Transfer legs must be managed through /api/v1/transfers");
        }
    }

    private void ensureStandaloneTransaction(FinancialTransaction transaction) {
        if (transaction.getTransferId() != null) {
            throw new TransactionConflictException("Transfer legs must be managed through /api/v1/transfers");
        }
    }

    private Map<UUID, FinancialAccount> lockOwnedAccounts(UUID ownerId, Collection<UUID> accountIds) {
        Map<UUID, FinancialAccount> accounts = new HashMap<>();
        accountIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(accountId -> accounts.put(accountId,
                        accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId)
                                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId))));
        return accounts;
    }

    private void ensureActiveAccount(FinancialAccount account) {
        if (account.getStatus() == AccountStatus.ARCHIVED) {
            throw new TransactionConflictException(
                    "An archived financial account cannot receive a transaction: " + account.getId()
            );
        }
    }

    private void validateDate(LocalDate transactionDate, FinancialAccount account) {
        if (transactionDate.isBefore(account.getOpeningDate())) {
            throw new TransactionConflictException(
                    "Transaction date cannot precede the account opening date: " + account.getOpeningDate()
            );
        }
        if (transactionDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new TransactionConflictException("Transaction date cannot be in the future: " + transactionDate);
        }
    }

    private void validateCategory(UUID categoryId, UUID ownerId, TransactionType type, boolean requireActive) {
        if (categoryId == null) {
            return;
        }
        TransactionCategory category = categoryRepository.findByIdAndOwnerId(categoryId, ownerId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (requireActive && category.getStatus() == CategoryStatus.ARCHIVED) {
            throw new TransactionConflictException(
                    "An archived category cannot be assigned to a transaction: " + categoryId
            );
        }
        boolean compatible = category.getApplicability() == CategoryApplicability.BOTH
                || category.getApplicability().name().equals(type.name());
        if (!compatible) {
            throw new TransactionConflictException(
                    "Category applicability is incompatible with transaction type: " + categoryId
            );
        }
    }
}
