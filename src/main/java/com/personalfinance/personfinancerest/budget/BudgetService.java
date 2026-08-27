package com.personalfinance.personfinancerest.budget;

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
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class BudgetService {

    private final BudgetRepository repository;
    private final TransactionCategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final BudgetWriteLock writeLock;

    BudgetService(BudgetRepository repository,
                  TransactionCategoryRepository categoryRepository,
                  CurrentUserProvider currentUserProvider,
                  BudgetWriteLock writeLock) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
        this.writeLock = writeLock;
    }

    @Transactional
    BudgetResponse create(CreateBudgetRequest request) {
        validateMonthlyPeriod(request.startDate(), request.endDate());
        UUID ownerId = currentUserProvider.userId();
        writeLock.acquire(ownerId);
        Budget budget = new Budget(
                UUID.randomUUID(), ownerId, request.name(), MoneyValues.currencyCode(request.currency()),
                request.startDate(), request.endDate()
        );
        List<CreateBudgetLineRequest> lines = request.lines() == null ? List.of() : request.lines();
        ensureUniqueCategories(lines.stream().map(CreateBudgetLineRequest::categoryId).toList());
        for (CreateBudgetLineRequest line : lines) {
            validateNewCategory(line.categoryId(), ownerId);
            budget.addLine(line.categoryId(), plannedAmount(line.plannedAmount(), "lines"));
        }
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse copy(UUID sourceBudgetId, CopyBudgetRequest request) {
        UUID ownerId = currentUserProvider.userId();
        writeLock.acquire(ownerId);
        Budget source = findOwnedBudget(sourceBudgetId);
        YearMonth month = request.month();
        repository.findFirstByOwnerIdAndStartDateOrderByCreatedAtAscIdAsc(ownerId, month.atDay(1))
                .ifPresent(existing -> {
                    throw new BudgetTargetMonthConflictException(month, existing.getId());
                });

        Budget copy = new Budget(UUID.randomUUID(), ownerId, source.getName(), source.getCurrency(),
                month.atDay(1), month.atEndOfMonth());
        List<BudgetLine> activeLines = source.getLines().stream()
                .filter(line -> line.getStatus() == BudgetStatus.ACTIVE)
                .sorted(Comparator.comparingInt(BudgetLine::getPosition))
                .toList();
        for (BudgetLine line : activeLines) {
            validateNewCategory(line.getCategoryId(), ownerId);
            copy.addLine(line.getCategoryId(), plannedAmount(line.getPlannedAmount(), "lines"));
        }
        return BudgetResponse.from(save(copy));
    }

    @Transactional(readOnly = true)
    List<BudgetResponse> findAll(BudgetStatusFilter status) {
        UUID ownerId = currentUserProvider.userId();
        List<Budget> budgets = switch (status) {
            case ACTIVE -> repository.findAllByOwnerIdAndArchivedAtIsNullOrderByStartDateDescNameAsc(ownerId);
            case ARCHIVED -> repository.findAllByOwnerIdAndArchivedAtIsNotNullOrderByStartDateDescNameAsc(ownerId);
            case ALL -> repository.findAllByOwnerIdOrderByStartDateDescNameAsc(ownerId);
        };
        return budgets.stream().map(BudgetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    BudgetResponse findById(UUID budgetId) {
        return BudgetResponse.from(findOwnedBudget(budgetId));
    }

    @Transactional
    BudgetResponse update(UUID budgetId, UpdateBudgetRequest request) {
        writeLock.acquire(currentUserProvider.userId());
        Budget budget = findOwnedBudget(budgetId);
        ensureMutable(budget);
        validateMonthlyPeriod(request.startDate(), request.endDate());
        budget.replace(
                request.name(), MoneyValues.currencyCode(request.currency()), request.startDate(), request.endDate()
        );
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse archive(UUID budgetId) {
        Budget budget = findOwnedBudget(budgetId);
        budget.archive(Instant.now());
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse restore(UUID budgetId) {
        Budget budget = findOwnedBudget(budgetId);
        budget.restore();
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse addLine(UUID budgetId, CreateBudgetLineRequest request) {
        Budget budget = findOwnedBudget(budgetId);
        ensureMutable(budget);
        ensureCategoryAvailable(budget, request.categoryId(), null);
        validateNewCategory(request.categoryId(), budget.getOwnerId());
        budget.addLine(request.categoryId(), plannedAmount(request.plannedAmount(), "plannedAmount"));
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse updateLine(UUID budgetId, UUID lineId, UpdateBudgetLineRequest request) {
        Budget budget = findOwnedBudget(budgetId);
        ensureMutable(budget);
        BudgetLine line = findLine(budget, lineId);
        ensureCategoryAvailable(budget, request.categoryId(), lineId);
        if (!line.getCategoryId().equals(request.categoryId())) {
            validateNewCategory(request.categoryId(), budget.getOwnerId());
        }
        line.replace(request.categoryId(), plannedAmount(request.plannedAmount(), "plannedAmount"));
        budget.markChanged();
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse reorderLines(UUID budgetId, ReorderBudgetLinesRequest request) {
        Budget budget = findOwnedBudget(budgetId);
        ensureMutable(budget);
        List<UUID> currentIds = budget.getLines().stream().map(BudgetLine::getId).toList();
        List<UUID> requestedIds = request.lineIds();
        if (requestedIds.size() != currentIds.size()
                || new HashSet<>(requestedIds).size() != requestedIds.size()
                || !new HashSet<>(requestedIds).equals(new HashSet<>(currentIds))) {
            throw new InvalidBudgetRequestException(Map.of(
                    "lineIds", "lineIds must contain every retained budget line exactly once"
            ));
        }
        budget.reorder(requestedIds);
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse archiveLine(UUID budgetId, UUID lineId) {
        Budget budget = findOwnedBudget(budgetId);
        ensureMutable(budget);
        findLine(budget, lineId).archive(Instant.now());
        budget.markChanged();
        return BudgetResponse.from(save(budget));
    }

    @Transactional
    BudgetResponse restoreLine(UUID budgetId, UUID lineId) {
        Budget budget = findOwnedBudget(budgetId);
        ensureMutable(budget);
        findLine(budget, lineId).restore();
        budget.markChanged();
        return BudgetResponse.from(save(budget));
    }

    private Budget findOwnedBudget(UUID budgetId) {
        return repository.findByIdAndOwnerId(budgetId, currentUserProvider.userId())
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
    }

    private BudgetLine findLine(Budget budget, UUID lineId) {
        return budget.getLines().stream().filter(line -> line.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new BudgetLineNotFoundException(lineId));
    }

    private void validateMonthlyPeriod(LocalDate startDate, LocalDate endDate) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (startDate != null && startDate.getDayOfMonth() != 1) {
            errors.put("startDate", "A monthly budget must begin on the first day of the month");
        }
        if (startDate != null && endDate != null) {
            LocalDate expectedEnd = YearMonth.from(startDate).atEndOfMonth();
            if (!endDate.equals(expectedEnd)) {
                errors.put("endDate", "A monthly budget must end on the last day of its start month");
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidBudgetRequestException(errors);
        }
    }

    private void ensureUniqueCategories(List<UUID> categoryIds) {
        Set<UUID> unique = new HashSet<>(categoryIds);
        if (unique.size() != categoryIds.size()) {
            throw new InvalidBudgetRequestException(Map.of("lines", "A category may appear only once per budget"));
        }
    }

    private void ensureCategoryAvailable(Budget budget, UUID categoryId, UUID excludedLineId) {
        boolean used = budget.getLines().stream()
                .filter(line -> excludedLineId == null || !line.getId().equals(excludedLineId))
                .anyMatch(line -> line.getCategoryId().equals(categoryId));
        if (used) {
            throw new BudgetConflictException("A category may appear only once per budget: " + categoryId);
        }
    }

    private void validateNewCategory(UUID categoryId, UUID ownerId) {
        TransactionCategory category = categoryRepository.findByIdAndOwnerId(categoryId, ownerId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            throw new BudgetConflictException("An archived category cannot be assigned to a budget: " + categoryId);
        }
        if (category.getApplicability() == CategoryApplicability.INCOME) {
            throw new BudgetConflictException("An income-only category cannot be assigned to a budget: " + categoryId);
        }
    }

    private void ensureMutable(Budget budget) {
        if (budget.getStatus() == BudgetStatus.ARCHIVED) {
            throw new BudgetConflictException("An archived budget must be restored before it can be changed");
        }
    }

    private BigDecimal plannedAmount(BigDecimal value, String field) {
        try {
            BigDecimal amount = MoneyValues.amountOrZero(value);
            if (amount.signum() < 0) {
                throw new InvalidBudgetRequestException(Map.of(field, "Planned amount cannot be negative"));
            }
            return amount;
        } catch (ArithmeticException exception) {
            throw new InvalidBudgetRequestException(Map.of(field, "Planned amount must use at most two decimals"));
        }
    }

    private Budget save(Budget budget) {
        try {
            return repository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException exception) {
            throw new BudgetConflictException("Budget data conflicts with an existing retained line");
        }
    }
}
