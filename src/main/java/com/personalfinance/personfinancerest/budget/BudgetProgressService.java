package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.CategoryNotFoundException;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class BudgetProgressService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final BudgetRepository budgetRepository;
    private final FinancialAccountRepository accountRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final BudgetSpendingSource spendingSource;
    private final CurrentUserProvider currentUserProvider;

    BudgetProgressService(BudgetRepository budgetRepository,
                          FinancialAccountRepository accountRepository,
                          TransactionCategoryRepository categoryRepository,
                          BudgetSpendingSource spendingSource,
                          CurrentUserProvider currentUserProvider) {
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.spendingSource = spendingSource;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    BudgetProgressResponse calculate(UUID budgetId, UUID accountId, UUID categoryId) {
        UUID ownerId = currentUserProvider.userId();
        Budget budget = budgetRepository.findByIdAndOwnerId(budgetId, ownerId)
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
        validateAccount(ownerId, accountId, budget.getCurrency());

        List<TransactionCategory> categories = categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId);
        Map<UUID, UUID> parentByCategory = new HashMap<>();
        categories.forEach(category -> parentByCategory.put(category.getId(), category.getParentId()));
        Set<UUID> filterCategoryIds = categoryId == null
                ? null : descendants(categoryId, parentByCategory);

        List<BudgetLine> activeLines = budget.getLines().stream()
                .filter(line -> line.getStatus() == BudgetStatus.ACTIVE)
                .sorted(Comparator.comparingInt(BudgetLine::getPosition))
                .toList();
        Map<UUID, MutableProgress> lineProgress = new LinkedHashMap<>();
        activeLines.forEach(line -> lineProgress.put(line.getId(), new MutableProgress()));
        Map<UUID, MutableProgress> unbudgeted = new LinkedHashMap<>();

        List<BudgetSpendingAllocation> allocations = spendingSource.findExpenseAllocations(
                ownerId, budget.getCurrency(), budget.getStartDate(), budget.getEndDate(), accountId
        );
        for (BudgetSpendingAllocation allocation : allocations) {
            if (filterCategoryIds != null && !filterCategoryIds.contains(allocation.categoryId())) {
                continue;
            }
            BudgetLine line = mostSpecificLine(activeLines, allocation.categoryId(), parentByCategory);
            MutableProgress target = line == null
                    ? unbudgeted.computeIfAbsent(allocation.categoryId(), ignored -> new MutableProgress())
                    : lineProgress.get(line.getId());
            target.add(allocation.amount(), allocation.transactionId());
        }

        List<BudgetLineProgressResponse> lines = activeLines.stream().map(line -> {
            MutableProgress progress = lineProgress.get(line.getId());
            BigDecimal planned = line.getPlannedAmount();
            Set<UUID> categoryIds = descendants(line.getCategoryId(), parentByCategory);
            return new BudgetLineProgressResponse(
                    line.getId(), line.getCategoryId(), line.getPosition(), planned, progress.actual,
                    planned.subtract(progress.actual), percentage(progress.actual, planned),
                    drillDown(budget, accountId, categoryIds, progress.transactionIds,
                            "line", line.getId(), null, false)
            );
        }).toList();

        List<UnbudgetedProgressResponse> unbudgetedRows = unbudgeted.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(Comparator.comparing(UUID::toString))))
                .map(entry -> new UnbudgetedProgressResponse(
                        entry.getKey(), entry.getValue().actual,
                        drillDown(budget, accountId,
                                entry.getKey() == null ? Set.of() : Set.of(entry.getKey()),
                                entry.getValue().transactionIds,
                                "unbudgeted", null, entry.getKey(), entry.getKey() == null)
                )).toList();

        BigDecimal planned = activeLines.stream().map(BudgetLine::getPlannedAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal budgetedActual = lineProgress.values().stream()
                .map(progress -> progress.actual).reduce(ZERO, BigDecimal::add);
        BigDecimal unbudgetedActual = unbudgeted.values().stream()
                .map(progress -> progress.actual).reduce(ZERO, BigDecimal::add);
        BigDecimal totalActual = budgetedActual.add(unbudgetedActual);
        LinkedHashSet<UUID> allTransactionIds = new LinkedHashSet<>();
        allocations.stream()
                .filter(allocation -> filterCategoryIds == null || filterCategoryIds.contains(allocation.categoryId()))
                .map(BudgetSpendingAllocation::transactionId)
                .forEach(allTransactionIds::add);

        return new BudgetProgressResponse(
                budget.getId(), ownerId, budget.getCurrency(), budget.getStartDate(), budget.getEndDate(),
                accountId, categoryId, planned, budgetedActual, unbudgetedActual, totalActual,
                planned.subtract(totalActual), percentage(totalActual, planned), lines, unbudgetedRows,
                drillDown(budget, accountId,
                        filterCategoryIds == null ? Set.of() : filterCategoryIds, allTransactionIds,
                        "overall", null, categoryId, false)
        );
    }

    private void validateAccount(UUID ownerId, UUID accountId, String budgetCurrency) {
        if (accountId == null) {
            return;
        }
        FinancialAccount account = accountRepository.findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
        if (!account.getCurrency().equalsIgnoreCase(budgetCurrency)) {
            throw new BudgetConflictException("Account currency must match budget currency: " + budgetCurrency);
        }
    }

    private Set<UUID> descendants(UUID rootId, Map<UUID, UUID> parentByCategory) {
        if (!parentByCategory.containsKey(rootId)) {
            throw new CategoryNotFoundException(rootId);
        }
        Set<UUID> result = new LinkedHashSet<>();
        result.add(rootId);
        boolean changed;
        do {
            changed = parentByCategory.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && result.contains(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .map(result::add)
                    .reduce(false, Boolean::logicalOr);
        } while (changed);
        return Set.copyOf(result);
    }

    private BudgetLine mostSpecificLine(List<BudgetLine> lines, UUID categoryId,
                                        Map<UUID, UUID> parentByCategory) {
        if (categoryId == null) {
            return null;
        }
        Map<UUID, Integer> distanceByAncestor = new HashMap<>();
        UUID current = categoryId;
        int distance = 0;
        while (current != null && !distanceByAncestor.containsKey(current)) {
            distanceByAncestor.put(current, distance++);
            current = parentByCategory.get(current);
        }
        return lines.stream()
                .filter(line -> distanceByAncestor.containsKey(line.getCategoryId()))
                .min(Comparator.comparingInt((BudgetLine line) -> distanceByAncestor.get(line.getCategoryId()))
                        .thenComparingInt(BudgetLine::getPosition))
                .orElse(null);
    }

    private BudgetProgressDrillDown drillDown(Budget budget, UUID accountId, Set<UUID> categoryIds,
                                               Set<UUID> transactionIds, String scope, UUID lineId,
                                               UUID categoryId, boolean uncategorized) {
        return new BudgetProgressDrillDown(
                budget.getStartDate(), budget.getEndDate(), accountId,
                categoryIds.stream().sorted(Comparator.comparing(UUID::toString)).toList(),
                "expense", "active", List.copyOf(transactionIds),
                transactionsPath(budget.getId(), accountId, scope, lineId, categoryId, uncategorized)
        );
    }

    private String transactionsPath(UUID budgetId, UUID accountId, String scope, UUID lineId,
                                    UUID categoryId, boolean uncategorized) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/api/v1/budgets/{budgetId}/progress/transactions")
                .queryParam("scope", scope);
        if (lineId != null) {
            builder.queryParam("lineId", lineId);
        }
        if (accountId != null) {
            builder.queryParam("accountId", accountId);
        }
        if (categoryId != null) {
            builder.queryParam("categoryId", categoryId);
        }
        if (uncategorized) {
            builder.queryParam("uncategorized", true);
        }
        return builder.buildAndExpand(budgetId).toUriString();
    }

    private BigDecimal percentage(BigDecimal actual, BigDecimal planned) {
        if (planned.signum() == 0) {
            return null;
        }
        return actual.multiply(BigDecimal.valueOf(100)).divide(planned, 2, RoundingMode.HALF_UP);
    }

    private static final class MutableProgress {
        private BigDecimal actual = ZERO;
        private final Set<UUID> transactionIds = new LinkedHashSet<>();

        private void add(BigDecimal amount, UUID transactionId) {
            actual = actual.add(amount);
            transactionIds.add(transactionId);
        }
    }
}
