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
import java.util.Collections;
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
    private final BudgetCommitmentSource commitmentSource;
    private final CurrentUserProvider currentUserProvider;

    BudgetProgressService(BudgetRepository budgetRepository,
                          FinancialAccountRepository accountRepository,
                          TransactionCategoryRepository categoryRepository,
                          BudgetSpendingSource spendingSource,
                          BudgetCommitmentSource commitmentSource,
                          CurrentUserProvider currentUserProvider) {
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.spendingSource = spendingSource;
        this.commitmentSource = commitmentSource;
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
        Map<UUID, MutableCommitments> lineCommitments = new LinkedHashMap<>();
        activeLines.forEach(line -> lineCommitments.put(line.getId(), new MutableCommitments()));
        Map<UUID, MutableCommitments> unbudgetedCommitments = new LinkedHashMap<>();
        Map<UUID, MutableCategoryProgress> categoryProgress = new LinkedHashMap<>();
        categories.forEach(category -> categoryProgress.put(category.getId(), new MutableCategoryProgress()));

        List<BudgetScheduledCommitment> commitments = commitmentSource.findScheduledCommitments(
                ownerId, budget.getCurrency(), budget.getStartDate(), budget.getEndDate(), accountId
        ).stream().filter(commitment -> filterCategoryIds == null
                        || filterCategoryIds.contains(commitment.categoryId()))
                .toList();
        Map<UUID, BudgetScheduledCommitment> commitmentByLinkedTransaction = new HashMap<>();
        for (BudgetScheduledCommitment commitment : commitments) {
            BudgetLine line = mostSpecificLine(activeLines, commitment.categoryId(), parentByCategory);
            MutableCommitments target = line == null
                    ? unbudgetedCommitments.computeIfAbsent(
                            commitment.categoryId(), ignored -> new MutableCommitments())
                    : lineCommitments.get(line.getId());
            target.add(commitment);
            MutableCategoryProgress exactCategory = categoryProgress.get(commitment.categoryId());
            if (exactCategory != null) {
                exactCategory.scheduledTarget = exactCategory.scheduledTarget.add(commitment.amount());
                if (commitment.satisfied() && commitment.actualAmount() != null) {
                    exactCategory.billActual = exactCategory.billActual.add(commitment.actualAmount());
                }
            }
            if (commitment.satisfied() && commitment.linkedTransactionId() != null) {
                commitmentByLinkedTransaction.put(commitment.linkedTransactionId(), commitment);
            }
        }

        List<BudgetSpendingAllocation> allocations = spendingSource.findExpenseAllocations(
                ownerId, budget.getCurrency(), budget.getStartDate(), budget.getEndDate(), accountId
        );
        for (BudgetSpendingAllocation allocation : allocations) {
            if (filterCategoryIds != null && !filterCategoryIds.contains(allocation.categoryId())) {
                continue;
            }
            if (commitmentByLinkedTransaction.containsKey(allocation.transactionId())) {
                continue;
            }
            BudgetLine line = mostSpecificLine(activeLines, allocation.categoryId(), parentByCategory);
            MutableProgress target = line == null
                    ? unbudgeted.computeIfAbsent(allocation.categoryId(), ignored -> new MutableProgress())
                    : lineProgress.get(line.getId());
            target.add(allocation.amount(), allocation.transactionId());
            MutableCategoryProgress exactCategory = categoryProgress.get(allocation.categoryId());
            if (exactCategory != null) {
                exactCategory.flexibleActual = exactCategory.flexibleActual.add(allocation.amount());
            }
        }

        activeLines.forEach(line -> {
            MutableCategoryProgress exactCategory = categoryProgress.get(line.getCategoryId());
            if (exactCategory == null) {
                throw new BudgetConflictException(
                        "Budget line references a missing category: " + line.getCategoryId()
                );
            }
            exactCategory.line = line;
            exactCategory.planned = line.getPlannedAmount();
        });

        List<BudgetLineProgressResponse> lines = activeLines.stream().map(line -> {
            MutableProgress progress = lineProgress.get(line.getId());
            MutableCommitments scheduled = lineCommitments.get(line.getId());
            BigDecimal planned = line.getPlannedAmount();
            BigDecimal totalBudgeted = planned.add(scheduled.total);
            BigDecimal actual = progress.actual.add(scheduled.actual);
            BigDecimal projectedUsage = actual.add(scheduled.outstanding);
            Set<UUID> categoryIds = descendants(line.getCategoryId(), parentByCategory);
            return new BudgetLineProgressResponse(
                    line.getId(), line.getCategoryId(), line.getPosition(), planned,
                    scheduled.total, scheduled.total, scheduled.outstanding, totalBudgeted,
                    planned.subtract(scheduled.total), scheduled.total.compareTo(planned) > 0,
                    List.copyOf(scheduled.items), progress.actual, scheduled.actual, actual,
                    totalBudgeted.subtract(actual), percentage(actual, totalBudgeted),
                    percentage(actual, totalBudgeted), projectedUsage,
                    totalBudgeted.subtract(projectedUsage), percentage(projectedUsage, totalBudgeted),
                    drillDown(budget, accountId, categoryIds,
                            combinedTransactionIds(progress.transactionIds, scheduled.transactionIds),
                            "line", line.getId(), null, false)
            );
        }).toList();

        List<UnbudgetedCommitmentResponse> unbudgetedCommitmentRows = unbudgetedCommitments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .map(entry -> {
                    MutableCommitments scheduled = entry.getValue();
                    BigDecimal totalBudgeted = scheduled.total;
                    BigDecimal projectedUsage = scheduled.actual.add(scheduled.outstanding);
                    return new UnbudgetedCommitmentResponse(
                            entry.getKey(), scheduled.total, scheduled.total, scheduled.outstanding,
                            totalBudgeted, scheduled.actual, scheduled.actual,
                            totalBudgeted.subtract(scheduled.actual),
                            percentage(scheduled.actual, totalBudgeted), projectedUsage,
                            totalBudgeted.subtract(projectedUsage), percentage(projectedUsage, totalBudgeted),
                            List.copyOf(scheduled.items)
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

        List<BudgetProgressComponentResponse> components = new ArrayList<>();
        activeLines.forEach(line -> components.add(flexibleComponent(
                budget, accountId, line, lineProgress.get(line.getId()), parentByCategory
        )));
        commitments.forEach(commitment -> components.add(recurringComponent(
                budget, accountId, commitment
        )));

        BigDecimal planned = activeLines.stream().map(BudgetLine::getPlannedAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal committed = commitments.stream()
                .map(BudgetScheduledCommitment::amount).reduce(ZERO, BigDecimal::add);
        BigDecimal outstandingCommitted = commitments.stream()
                .map(BudgetScheduledCommitment::outstandingAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal flexibleActual = lineProgress.values().stream()
                .map(progress -> progress.actual).reduce(ZERO, BigDecimal::add);
        BigDecimal billActual = commitments.stream()
                .filter(BudgetScheduledCommitment::satisfied)
                .map(BudgetScheduledCommitment::actualAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal budgetedActual = flexibleActual.add(billActual);
        BigDecimal unbudgetedActual = unbudgeted.values().stream()
                .map(progress -> progress.actual).reduce(ZERO, BigDecimal::add);
        BigDecimal totalActual = budgetedActual.add(unbudgetedActual);
        BigDecimal totalBudgeted = planned.add(committed);
        BigDecimal projectedUsage = totalActual.add(outstandingCommitted);
        LinkedHashSet<UUID> allTransactionIds = new LinkedHashSet<>();
        allocations.stream()
                .filter(allocation -> filterCategoryIds == null || filterCategoryIds.contains(allocation.categoryId()))
                .map(BudgetSpendingAllocation::transactionId)
                .forEach(allTransactionIds::add);
        commitments.stream()
                .filter(BudgetScheduledCommitment::satisfied)
                .map(BudgetScheduledCommitment::linkedTransactionId)
                .filter(java.util.Objects::nonNull)
                .forEach(allTransactionIds::add);
        List<BudgetCategoryProgressResponse> hierarchy = buildHierarchy(categories, categoryProgress);

        return new BudgetProgressResponse(
                budget.getId(), ownerId, budget.getCurrency(), budget.getStartDate(), budget.getEndDate(),
                accountId, categoryId, planned, committed, committed, outstandingCommitted, totalBudgeted,
                planned.subtract(committed),
                committed.compareTo(planned) > 0, flexibleActual, billActual,
                budgetedActual, unbudgetedActual, totalActual,
                totalBudgeted.subtract(totalActual), percentage(totalActual, totalBudgeted),
                percentage(totalActual, totalBudgeted), projectedUsage,
                totalBudgeted.subtract(projectedUsage), percentage(projectedUsage, totalBudgeted),
                lines, List.copyOf(components), unbudgetedRows,
                unbudgetedCommitmentRows, hierarchy,
                drillDown(budget, accountId,
                        filterCategoryIds == null ? Set.of() : filterCategoryIds, allTransactionIds,
                        "overall", null, categoryId, false)
        );
    }

    private List<BudgetCategoryProgressResponse> buildHierarchy(
            List<TransactionCategory> categories,
            Map<UUID, MutableCategoryProgress> progressByCategory
    ) {
        List<TransactionCategory> orderedCategories = categories.stream()
                .sorted(Comparator.comparing(TransactionCategory::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(category -> category.getId().toString()))
                .toList();
        Map<UUID, TransactionCategory> categoryById = new LinkedHashMap<>();
        Map<UUID, List<TransactionCategory>> childrenByParent = new LinkedHashMap<>();
        List<TransactionCategory> roots = new ArrayList<>();
        for (TransactionCategory category : orderedCategories) {
            categoryById.put(category.getId(), category);
        }
        for (TransactionCategory category : orderedCategories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                if (!categoryById.containsKey(category.getParentId())) {
                    throw new BudgetConflictException(
                            "Category hierarchy is incomplete at parent: " + category.getParentId()
                    );
                }
                childrenByParent.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category);
            }
        }

        Set<UUID> built = new LinkedHashSet<>();
        List<BudgetCategoryProgressResponse> hierarchy = roots.stream()
                .map(root -> buildCategoryProgress(
                        root, childrenByParent, progressByCategory, List.of(), false,
                        new LinkedHashSet<>(), built
                ))
                .toList();
        if (built.size() != orderedCategories.size()) {
            throw new BudgetConflictException("Category hierarchy contains a circular relationship");
        }
        return hierarchy;
    }

    private BudgetCategoryProgressResponse buildCategoryProgress(
            TransactionCategory category,
            Map<UUID, List<TransactionCategory>> childrenByParent,
            Map<UUID, MutableCategoryProgress> progressByCategory,
            List<BudgetCategoryPathSegment> parentPath,
            boolean hasAllocatedAncestor,
            Set<UUID> activePath,
            Set<UUID> built
    ) {
        if (!activePath.add(category.getId())) {
            throw new BudgetConflictException(
                    "Category hierarchy contains a circular relationship at: " + category.getId()
            );
        }
        MutableCategoryProgress direct = progressByCategory.get(category.getId());
        boolean allocated = direct.line != null;
        List<BudgetCategoryPathSegment> path = new ArrayList<>(parentPath);
        path.add(new BudgetCategoryPathSegment(category.getId(), category.getName()));
        List<BudgetCategoryProgressResponse> children = childrenByParent
                .getOrDefault(category.getId(), List.of()).stream()
                .map(child -> buildCategoryProgress(
                        child, childrenByParent, progressByCategory, path,
                        hasAllocatedAncestor || allocated, activePath, built
                ))
                .toList();

        BigDecimal directTarget = direct.planned.add(direct.scheduledTarget);
        BigDecimal directActual = direct.flexibleActual.add(direct.billActual);
        BigDecimal rollupTarget = children.stream()
                .map(BudgetCategoryProgressResponse::rollupTarget)
                .reduce(directTarget, BigDecimal::add);
        BigDecimal rollupActual = children.stream()
                .map(BudgetCategoryProgressResponse::rollupActual)
                .reduce(directActual, BigDecimal::add);
        int descendantAllocationCount = children.stream()
                .mapToInt(child -> child.descendantAllocationCount()
                        + (child.allocationState() == BudgetAllocationState.ALLOCATED ? 1 : 0))
                .sum();

        activePath.remove(category.getId());
        built.add(category.getId());
        return new BudgetCategoryProgressResponse(
                category.getId(), category.getName(), List.copyOf(path), category.getStatus(),
                allocated ? BudgetAllocationState.ALLOCATED
                        : hasAllocatedAncestor ? BudgetAllocationState.COVERED_BY_ANCESTOR
                        : BudgetAllocationState.UNBUDGETED,
                allocated ? direct.line.getId() : null,
                direct.planned, direct.scheduledTarget, directTarget, rollupTarget,
                direct.flexibleActual, direct.billActual, directActual, rollupActual,
                rollupTarget.subtract(rollupActual), percentage(rollupActual, rollupTarget),
                descendantAllocationCount, children
        );
    }

    private BudgetProgressComponentResponse flexibleComponent(
            Budget budget,
            UUID accountId,
            BudgetLine line,
            MutableProgress progress,
            Map<UUID, UUID> parentByCategory
    ) {
        BigDecimal target = line.getPlannedAmount();
        Set<UUID> categoryIds = descendants(line.getCategoryId(), parentByCategory);
        return new BudgetProgressComponentResponse(
                "line:" + line.getId(), BudgetComponentSource.FLEXIBLE,
                line.getId(), null, null, line.getCategoryId(), line.getPosition(),
                null, null, target, progress.actual, target.subtract(progress.actual),
                percentage(progress.actual, target), progress.actual,
                target.subtract(progress.actual), percentage(progress.actual, target),
                null, null, null,
                drillDown(budget, accountId, categoryIds, progress.transactionIds,
                        "line", line.getId(), null, false)
        );
    }

    private BudgetProgressComponentResponse recurringComponent(
            Budget budget,
            UUID accountId,
            BudgetScheduledCommitment commitment
    ) {
        BigDecimal actual = commitment.satisfied() && commitment.actualAmount() != null
                ? commitment.actualAmount() : ZERO;
        BigDecimal projectedUsage = commitment.satisfied() ? actual : commitment.amount();
        Set<UUID> transactionIds = commitment.satisfied() && commitment.linkedTransactionId() != null
                ? Set.of(commitment.linkedTransactionId()) : Set.of();
        return new BudgetProgressComponentResponse(
                "occurrence:" + commitment.occurrenceKey(), BudgetComponentSource.RECURRING,
                null, commitment.occurrenceKey(), commitment.recurringExpenseId(),
                commitment.categoryId(), null, commitment.name(), commitment.dueDate(),
                commitment.amount(), actual, commitment.amount().subtract(actual),
                percentage(actual, commitment.amount()), projectedUsage,
                commitment.amount().subtract(projectedUsage),
                percentage(projectedUsage, commitment.amount()),
                commitment.satisfied() ? BudgetComponentStatus.SATISFIED
                        : BudgetComponentStatus.OUTSTANDING,
                commitment.variance(), commitment.linkedTransactionId(),
                componentDrillDown(budget, accountId, commitment, transactionIds)
        );
    }

    private BudgetProgressDrillDown componentDrillDown(
            Budget budget,
            UUID accountId,
            BudgetScheduledCommitment commitment,
            Set<UUID> transactionIds
    ) {
        return new BudgetProgressDrillDown(
                budget.getStartDate(), budget.getEndDate(), accountId,
                commitment.categoryId() == null ? List.of() : List.of(commitment.categoryId()),
                "expense", "active", List.copyOf(transactionIds),
                transactionsPath(budget.getId(), accountId, "component", null,
                        null, false, commitment.occurrenceKey())
        );
    }

    private Set<UUID> combinedTransactionIds(Set<UUID> first, Set<UUID> second) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Collections.unmodifiableSet(result);
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
                transactionsPath(budget.getId(), accountId, scope, lineId, categoryId, uncategorized, null)
        );
    }

    private String transactionsPath(UUID budgetId, UUID accountId, String scope, UUID lineId,
                                    UUID categoryId, boolean uncategorized, String occurrenceKey) {
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
        if (occurrenceKey != null) {
            builder.queryParam("occurrenceKey", occurrenceKey);
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

    private static final class MutableCommitments {
        private BigDecimal total = ZERO;
        private BigDecimal outstanding = ZERO;
        private BigDecimal actual = ZERO;
        private final List<BudgetScheduledCommitment> items = new ArrayList<>();
        private final Set<UUID> transactionIds = new LinkedHashSet<>();

        private void add(BudgetScheduledCommitment commitment) {
            total = total.add(commitment.amount());
            outstanding = outstanding.add(commitment.outstandingAmount());
            if (commitment.satisfied() && commitment.actualAmount() != null) {
                actual = actual.add(commitment.actualAmount());
            }
            if (commitment.satisfied() && commitment.linkedTransactionId() != null) {
                transactionIds.add(commitment.linkedTransactionId());
            }
            items.add(commitment);
        }
    }

    private static final class MutableCategoryProgress {
        private BudgetLine line;
        private BigDecimal planned = ZERO;
        private BigDecimal scheduledTarget = ZERO;
        private BigDecimal flexibleActual = ZERO;
        private BigDecimal billActual = ZERO;
    }
}
