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
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseMatchingService;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseOccurrenceReference;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseTransactionSource;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final RecurringExpenseMatchingService recurringExpenseMatchingService;
    private final RecurringExpenseTransactionSource recurringExpenseTransactionSource;
    private final CurrentUserProvider currentUserProvider;

    FinancialTransactionService(FinancialTransactionRepository repository,
                                FinancialAccountRepository accountRepository,
                                TransactionCategoryRepository categoryRepository,
                                RecurringExpenseMatchingService recurringExpenseMatchingService,
                                RecurringExpenseTransactionSource recurringExpenseTransactionSource,
                                CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.recurringExpenseMatchingService = recurringExpenseMatchingService;
        this.recurringExpenseTransactionSource = recurringExpenseTransactionSource;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    TransactionResponse create(CreateTransactionRequest request) {
        ensureStandaloneType(request.type());
        UUID ownerId = currentUserProvider.userId();
        FinancialAccount account = lockOwnedAccounts(ownerId, Set.of(request.accountId())).get(request.accountId());
        ensureActiveAccount(account);
        validateDate(request.transactionDate(), account);
        BigDecimal amount = transactionAmount(request.amount(), request.type());
        List<FinancialTransaction.SplitReplacement> splits = validateAllocation(
                null, request.categoryId(), request.splits(), ownerId, request.type(), amount, true
        );
        if (splits.isEmpty()) {
            validateCategory(request.categoryId(), ownerId, request.type(), true);
        }

        FinancialTransaction transaction = new FinancialTransaction(
                UUID.randomUUID(), ownerId, request.accountId(), splits.isEmpty() ? request.categoryId() : null,
                amount, request.type(), request.transactionDate(),
                request.description(), request.merchantPayee(), request.notes(), request.externalReference()
        );
        transaction.replaceSplits(splits);
        FinancialTransaction saved = repository.saveAndFlush(transaction);
        RecurringExpenseOccurrenceReference occurrence = recurringExpenseMatchingService
                .applyTransactionSelection(
                        recurringExpenseTransactionSource.findOwned(ownerId, saved.getId()),
                        request.recurringExpenseOccurrence(), false);
        account.applyBalanceDelta(saved.balanceImpact());
        accountRepository.saveAndFlush(account);
        return TransactionResponse.from(saved, occurrence);
    }

    @Transactional(readOnly = true)
    TransactionPageResponse search(TransactionSearchCriteria criteria) {
        UUID ownerId = currentUserProvider.userId();
        Page<FinancialTransaction> result = repository.findAll(
                FinancialTransactionSpecifications.matching(ownerId, criteria),
                PageRequest.of(criteria.page(), criteria.size(), criteria.pageableSort())
        );
        return TransactionPageResponse.from(result, criteria,
                recurringExpenseMatchingService.referencesForTransactions(
                        ownerId, result.getContent().stream().map(FinancialTransaction::getId).toList()));
    }

    @Transactional(readOnly = true)
    TransactionResponse findById(UUID transactionId) {
        FinancialTransaction transaction = findOwnedTransaction(transactionId);
        return TransactionResponse.from(transaction,
                recurringExpenseMatchingService.referenceForTransaction(
                        transaction.getOwnerId(), transaction.getId()));
    }

    @Transactional(readOnly = true)
    List<TransactionSummaryResponse> summarize(LocalDate from, LocalDate to) {
        return summarize(from, to, null, null, null);
    }

    @Transactional(readOnly = true)
    List<TransactionSummaryResponse> summarize(LocalDate from, LocalDate to, UUID accountId,
                                               UUID categoryId, String type) {
        TransactionSearchCriteria.validateRanges(from, to, null, null);
        return repository.summarize(
                        currentUserProvider.userId(), from, to,
                        TransactionType.INCOME, TransactionType.EXPENSE,
                        accountId, categoryId, TransactionSearchCriteria.parseType(type)
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

        BigDecimal amount = transactionAmount(request.amount(), request.type());
        List<FinancialTransaction.SplitReplacement> splits = validateAllocation(
                transaction, request.categoryId(), request.splits(), ownerId, request.type(), amount, false
        );
        if (splits.isEmpty()) {
            boolean categoryChanged = !Objects.equals(transaction.getCategoryId(), request.categoryId())
                    || !transaction.getSplits().isEmpty();
            validateCategory(request.categoryId(), ownerId, request.type(), categoryChanged);
        }

        BigDecimal oldImpact = transaction.getStatus() == TransactionStatus.ACTIVE
                ? transaction.balanceImpact() : BigDecimal.ZERO;
        transaction.replace(
                request.accountId(), splits.isEmpty() ? request.categoryId() : null, amount,
                request.type(), request.transactionDate(), request.description(), request.merchantPayee(),
                request.notes(), request.externalReference()
        );
        transaction.replaceSplits(splits);
        BigDecimal newImpact = transaction.getStatus() == TransactionStatus.ACTIVE
                ? transaction.balanceImpact() : BigDecimal.ZERO;

        if (oldAccountId.equals(request.accountId())) {
            newAccount.applyBalanceDelta(newImpact.subtract(oldImpact));
        } else {
            accounts.get(oldAccountId).applyBalanceDelta(oldImpact.negate());
            newAccount.applyBalanceDelta(newImpact);
        }

        FinancialTransaction saved = repository.saveAndFlush(transaction);
        RecurringExpenseOccurrenceReference occurrence = recurringExpenseMatchingService
                .applyTransactionSelection(
                        recurringExpenseTransactionSource.findOwned(ownerId, saved.getId()),
                        request.recurringExpenseOccurrence(), true);
        accountRepository.saveAllAndFlush(accounts.values());
        return TransactionResponse.from(saved, occurrence);
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
        FinancialTransaction saved = repository.saveAndFlush(transaction);
        return TransactionResponse.from(saved,
                recurringExpenseMatchingService.referenceForTransaction(ownerId, saved.getId()));
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
            if (transaction.getSplits().isEmpty()) {
                validateCategory(transaction.getCategoryId(), ownerId, transaction.getType(), false);
            } else {
                validateAllocation(
                        transaction, null,
                        transaction.getSplits().stream()
                                .map(split -> new TransactionSplitRequest(
                                        split.getId(), split.getCategoryId(), split.getAmount()))
                                .toList(),
                        ownerId, transaction.getType(), transaction.getAmount(), false
                );
            }
            recurringExpenseMatchingService.validateRestore(
                    recurringExpenseTransactionSource.findOwned(ownerId, transaction.getId()));
            account.applyBalanceDelta(transaction.balanceImpact());
            accountRepository.saveAndFlush(account);
            transaction.restore();
        }
        FinancialTransaction saved = repository.saveAndFlush(transaction);
        return TransactionResponse.from(saved,
                recurringExpenseMatchingService.referenceForTransaction(ownerId, saved.getId()));
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

    private List<FinancialTransaction.SplitReplacement> validateAllocation(
            FinancialTransaction transaction,
            UUID categoryId,
            List<TransactionSplitRequest> requestedSplits,
            UUID ownerId,
            TransactionType type,
            BigDecimal transactionAmount,
            boolean creating
    ) {
        List<TransactionSplitRequest> splits = requestedSplits == null ? List.of() : requestedSplits;
        if (splits.isEmpty()) {
            return List.of();
        }

        Map<String, String> errors = new LinkedHashMap<>();
        if (categoryId != null) {
            errors.put("categoryId", "categoryId must be null when splits are supplied");
        }
        if (type.isTransfer()) {
            errors.put("splits", "Transfers cannot be split");
        } else if (splits.size() < 2) {
            errors.put("splits", "At least two split rows are required");
        }

        Map<UUID, TransactionSplit> existingById = new HashMap<>();
        if (transaction != null) {
            transaction.getSplits().forEach(split -> existingById.put(split.getId(), split));
        }
        Set<UUID> suppliedIds = new HashSet<>();
        Set<UUID> categoryIds = new HashSet<>();
        List<FinancialTransaction.SplitReplacement> replacements = new ArrayList<>();
        BigDecimal allocatedTotal = BigDecimal.ZERO.setScale(2);

        for (int index = 0; index < splits.size(); index++) {
            TransactionSplitRequest split = splits.get(index);
            String fieldPrefix = "splits[" + index + "]";
            UUID splitId = split.id();
            TransactionSplit existing = splitId == null ? null : existingById.get(splitId);

            if (creating && splitId != null) {
                errors.put(fieldPrefix + ".id", "Split ids must be omitted when creating a transaction");
            } else if (!creating && splitId != null && existing == null) {
                errors.put(fieldPrefix + ".id", "Split id does not belong to this transaction");
            } else if (splitId != null && !suppliedIds.add(splitId)) {
                errors.put(fieldPrefix + ".id", "Split id is duplicated in the request");
            }

            BigDecimal amount = BigDecimal.ZERO.setScale(2);
            if (split.amount() != null) {
                try {
                    amount = MoneyValues.amountOrZero(split.amount());
                    allocatedTotal = allocatedTotal.add(amount);
                    if (amount.signum() == 0 || amount.signum() != transactionAmount.signum()) {
                        errors.put(fieldPrefix + ".amount",
                                "Split amount must be non-zero and use the transaction amount's sign");
                    }
                } catch (ArithmeticException exception) {
                    errors.put(fieldPrefix + ".amount", "Amount must use at most two decimal places");
                }
            }

            UUID splitCategoryId = split.categoryId();
            if (splitCategoryId != null) {
                if (!categoryIds.add(splitCategoryId)) {
                    errors.put("splits", "Split categories must be unique");
                }
                TransactionCategory splitCategory = categoryRepository.findByIdAndOwnerId(splitCategoryId, ownerId)
                        .orElse(null);
                if (splitCategory == null) {
                    errors.put(fieldPrefix + ".categoryId", "Category does not belong to the current owner");
                } else {
                    boolean categoryUnchanged = existing != null
                            && existing.getCategoryId().equals(splitCategoryId);
                    if (!categoryUnchanged && splitCategory.getStatus() == CategoryStatus.ARCHIVED) {
                        errors.put(fieldPrefix + ".categoryId", "An archived category cannot be assigned");
                    }
                    boolean compatible = splitCategory.getApplicability() == CategoryApplicability.BOTH
                            || splitCategory.getApplicability().name().equals(type.name());
                    if (!compatible) {
                        errors.put(fieldPrefix + ".categoryId",
                                "Category applicability is incompatible with transaction type");
                    }
                }
            }
            replacements.add(new FinancialTransaction.SplitReplacement(splitId, splitCategoryId, amount));
        }

        if (allocatedTotal.compareTo(transactionAmount) != 0) {
            errors.putIfAbsent("splits", "Split amounts must exactly equal the transaction amount");
        }
        if (!errors.isEmpty()) {
            throw new InvalidTransactionAllocationException(errors);
        }
        return List.copyOf(replacements);
    }

    private BigDecimal transactionAmount(BigDecimal value, TransactionType type) {
        BigDecimal amount = MoneyValues.amountOrZero(value);
        if (amount.signum() == 0) {
            throw new InvalidTransactionAllocationException(Map.of("amount", "Amount must be non-zero"));
        }
        if (type != TransactionType.EXPENSE && amount.signum() < 0) {
            throw new InvalidTransactionAllocationException(Map.of(
                    "amount", "Only expense transactions may use a negative amount for a refund or credit"
            ));
        }
        return amount;
    }
}
