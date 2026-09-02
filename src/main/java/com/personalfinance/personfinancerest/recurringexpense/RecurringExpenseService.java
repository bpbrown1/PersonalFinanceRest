package com.personalfinance.personfinancerest.recurringexpense;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class RecurringExpenseService {

    private final RecurringExpenseRepository repository;
    private final TransactionCategoryRepository categoryRepository;
    private final FinancialAccountRepository accountRepository;
    private final RecurringExpenseOccurrenceProjector projector;
    private final RecurringExpenseMatchingService matchingService;
    private final CurrentUserProvider currentUserProvider;

    RecurringExpenseService(RecurringExpenseRepository repository,
                            TransactionCategoryRepository categoryRepository,
                            FinancialAccountRepository accountRepository,
                            RecurringExpenseOccurrenceProjector projector,
                            RecurringExpenseMatchingService matchingService,
                            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.projector = projector;
        this.matchingService = matchingService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    RecurringExpenseResponse create(CreateRecurringExpenseRequest request) {
        UUID ownerId = currentUserProvider.userId();
        validateDates(request.anchorDate(), request.endDate());
        BigDecimal amount = amount(request.amount());
        String currency = MoneyValues.currencyCode(request.currency());
        validateAssociations(ownerId, request.categoryId(), request.accountId(), currency, null);
        RecurringExpense expense = new RecurringExpense(
                UUID.randomUUID(), ownerId, request.name(), amount, currency, request.categoryId(),
                request.accountId(), request.anchorDate(), request.endDate(), request.intervalMonths()
        );
        return RecurringExpenseResponse.from(save(expense));
    }

    @Transactional(readOnly = true)
    List<RecurringExpenseResponse> findAll(RecurringExpenseStatusFilter status) {
        UUID ownerId = currentUserProvider.userId();
        List<RecurringExpense> expenses = switch (status) {
            case ACTIVE -> repository.findAllByOwnerIdAndArchivedAtIsNullOrderByNameAscIdAsc(ownerId);
            case ARCHIVED -> repository.findAllByOwnerIdAndArchivedAtIsNotNullOrderByNameAscIdAsc(ownerId);
            case ALL -> repository.findAllByOwnerIdOrderByNameAscIdAsc(ownerId);
        };
        return expenses.stream().map(RecurringExpenseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    RecurringExpenseResponse findById(UUID id) {
        return RecurringExpenseResponse.from(findOwned(id));
    }

    @Transactional
    RecurringExpenseResponse update(UUID id, UpdateRecurringExpenseRequest request) {
        RecurringExpense expense = findOwned(id);
        ensureMutable(expense);
        validateDates(request.anchorDate(), request.endDate());
        BigDecimal amount = amount(request.amount());
        String currency = MoneyValues.currencyCode(request.currency());
        validateAssociations(expense.getOwnerId(), request.categoryId(), request.accountId(), currency, expense);
        expense.replace(request.name(), amount, currency, request.categoryId(), request.accountId(),
                request.anchorDate(), request.endDate(), request.intervalMonths());
        matchingService.validateExistingMatches(expense);
        return RecurringExpenseResponse.from(save(expense));
    }

    @Transactional
    RecurringExpenseResponse archive(UUID id) {
        RecurringExpense expense = findOwned(id);
        expense.archive(Instant.now());
        return RecurringExpenseResponse.from(save(expense));
    }

    @Transactional
    RecurringExpenseResponse restore(UUID id) {
        RecurringExpense expense = findOwned(id);
        if (expense.getStatus() == RecurringExpenseStatus.ARCHIVED) {
            validateAssociations(expense.getOwnerId(), expense.getCategoryId(), expense.getAccountId(),
                    expense.getCurrency(), null);
            expense.restore();
        }
        return RecurringExpenseResponse.from(save(expense));
    }

    @Transactional(readOnly = true)
    List<RecurringExpenseOccurrenceResponse> occurrences(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return project(currentUserProvider.userId(), null, from, to, null).stream()
                .map(projected -> projected.response)
                .toList();
    }

    @Transactional(readOnly = true)
    List<ProjectedOccurrence> project(UUID ownerId, String currency, LocalDate from, LocalDate to, UUID accountId) {
        validateRange(from, to);
        List<RecurringExpense> expenses = repository.findAllByOwnerIdOrderByNameAscIdAsc(ownerId).stream()
                .filter(expense -> currency == null || expense.getCurrency().equalsIgnoreCase(currency))
                .filter(expense -> accountId == null || accountId.equals(expense.getAccountId()))
                .toList();
        Map<RecurringExpenseMatchingService.OccurrenceIdentity,
                RecurringExpenseMatchingService.MatchDetails> matches = matchingService.matches(
                ownerId, expenses.stream().map(RecurringExpense::getId).toList(), from, to);
        return expenses.stream()
                .flatMap(expense -> projector.dueDates(expense, from, to).stream()
                        .map(dueDate -> projected(expense, dueDate, matches.get(
                                new RecurringExpenseMatchingService.OccurrenceIdentity(
                                        expense.getId(), dueDate)))))
                .sorted(Comparator.comparing((ProjectedOccurrence occurrence) -> occurrence.response.dueDate())
                        .thenComparing(occurrence -> occurrence.response.name())
                        .thenComparing(occurrence -> occurrence.response.recurringExpenseId()))
                .toList();
    }

    private ProjectedOccurrence projected(
            RecurringExpense expense, LocalDate dueDate,
            RecurringExpenseMatchingService.MatchDetails details) {
        RecurringExpenseOccurrenceResponse response = matchingService.response(expense, dueDate, details);
        return new ProjectedOccurrence(response);
    }

    private RecurringExpense findOwned(UUID id) {
        return repository.findByIdAndOwnerId(id, currentUserProvider.userId())
                .orElseThrow(() -> new RecurringExpenseNotFoundException(id));
    }

    private void validateDates(LocalDate anchorDate, LocalDate endDate) {
        if (anchorDate != null && endDate != null && endDate.isBefore(anchorDate)) {
            throw new InvalidRecurringExpenseRequestException(Map.of(
                    "endDate", "End date cannot precede the anchor date"
            ));
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (from == null) errors.put("from", "From date is required");
        if (to == null) errors.put("to", "To date is required");
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("dateRange", "From date must not be after to date");
        }
        if (!errors.isEmpty()) throw new InvalidRecurringExpenseRequestException(errors);
    }

    private void validateAssociations(UUID ownerId, UUID categoryId, UUID accountId, String currency,
                                      RecurringExpense existing) {
        TransactionCategory category = categoryRepository.findByIdAndOwnerId(categoryId, ownerId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        boolean retainedCategory = existing != null && existing.getCategoryId().equals(categoryId);
        if (!retainedCategory && category.getStatus() == CategoryStatus.ARCHIVED) {
            throw new RecurringExpenseConflictException("An archived category cannot be assigned");
        }
        if (!retainedCategory && category.getApplicability() == CategoryApplicability.INCOME) {
            throw new RecurringExpenseConflictException("An income-only category cannot be assigned");
        }
        if (accountId != null) {
            FinancialAccount account = accountRepository.findByIdAndOwnerId(accountId, ownerId)
                    .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
            boolean retainedAccount = existing != null && accountId.equals(existing.getAccountId());
            if (!retainedAccount && account.getStatus() == AccountStatus.ARCHIVED) {
                throw new RecurringExpenseConflictException("An archived account cannot be assigned");
            }
            if (!account.getCurrency().equalsIgnoreCase(currency)) {
                throw new RecurringExpenseConflictException("Account and recurring expense currencies must match");
            }
        }
    }

    private BigDecimal amount(BigDecimal value) {
        try {
            BigDecimal result = MoneyValues.amountOrZero(value);
            if (result.signum() < 0) {
                throw new InvalidRecurringExpenseRequestException(Map.of("amount", "Amount cannot be negative"));
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new InvalidRecurringExpenseRequestException(Map.of(
                    "amount", "Amount must use at most two decimals"
            ));
        }
    }

    private void ensureMutable(RecurringExpense expense) {
        if (expense.getStatus() == RecurringExpenseStatus.ARCHIVED) {
            throw new RecurringExpenseConflictException("An archived recurring expense must be restored before editing");
        }
    }

    private RecurringExpense save(RecurringExpense expense) {
        try {
            return repository.saveAndFlush(expense);
        } catch (DataIntegrityViolationException exception) {
            throw new RecurringExpenseConflictException("Recurring expense conflicts with retained data");
        }
    }

    record ProjectedOccurrence(RecurringExpenseOccurrenceResponse response) {
    }
}
