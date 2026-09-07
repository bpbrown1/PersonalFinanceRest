package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.CategoryStatus;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetProgressServiceTest {

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final FinancialAccountRepository accountRepository = mock(FinancialAccountRepository.class);
    private final TransactionCategoryRepository categoryRepository = mock(TransactionCategoryRepository.class);
    private final BudgetSpendingSource spendingSource = mock(BudgetSpendingSource.class);
    private final BudgetCommitmentSource commitmentSource = mock(BudgetCommitmentSource.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final BudgetProgressService service = new BudgetProgressService(
            budgetRepository, accountRepository, categoryRepository, spendingSource, commitmentSource,
            currentUserProvider
    );

    private final UUID ownerId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();
    private final UUID leafId = UUID.randomUUID();
    private final UUID unbudgetedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUserProvider.userId()).thenReturn(ownerId);
        when(commitmentSource.findScheduledCommitments(
                org.mockito.ArgumentMatchers.eq(ownerId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.nullable(UUID.class)
        )).thenReturn(List.of());
    }

    @Test
    void assignsToTheMostSpecificLineAndKeepsRefundsUnbudgetedSpendingAndOverages() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        BudgetLine parentLine = budget.addLine(parentId, amount("40.00"));
        BudgetLine childLine = budget.addLine(childId, amount("20.00"));
        BudgetLine zeroLine = budget.addLine(unbudgetedId, amount("0.00"));
        zeroLine.archive(java.time.Instant.now());

        TransactionCategory parent = category(parentId, null);
        TransactionCategory child = category(childId, parentId);
        TransactionCategory leaf = category(leafId, childId);
        TransactionCategory unbudgeted = category(unbudgetedId, null);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(parent, child, leaf, unbudgeted));

        UUID parentTransaction = UUID.randomUUID();
        UUID childTransaction = UUID.randomUUID();
        UUID refundTransaction = UUID.randomUUID();
        UUID unbudgetedTransaction = UUID.randomUUID();
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                allocation(parentTransaction, parentId, "10.00"),
                allocation(childTransaction, leafId, "30.00"),
                allocation(refundTransaction, leafId, "-5.00"),
                allocation(unbudgetedTransaction, unbudgetedId, "12.00")
        ));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.planned()).isEqualByComparingTo("60.00");
        assertThat(response.budgetedActual()).isEqualByComparingTo("35.00");
        assertThat(response.unbudgetedActual()).isEqualByComparingTo("12.00");
        assertThat(response.totalActual()).isEqualByComparingTo("47.00");
        assertThat(response.remaining()).isEqualByComparingTo("13.00");
        assertThat(response.percentageUsed()).isEqualByComparingTo("78.33");
        assertThat(response.lines()).extracting(BudgetLineProgressResponse::lineId)
                .containsExactly(parentLine.getId(), childLine.getId());
        assertThat(response.lines().get(0).actual()).isEqualByComparingTo("10.00");
        assertThat(response.lines().get(1).actual()).isEqualByComparingTo("25.00");
        assertThat(response.lines().get(1).remaining()).isEqualByComparingTo("-5.00");
        assertThat(response.lines().get(1).percentageUsed()).isEqualByComparingTo("125.00");
        assertThat(response.lines().get(1).drillDown().transactionIds())
                .containsExactly(childTransaction, refundTransaction);
        assertThat(response.unbudgeted()).singleElement().satisfies(row -> {
            assertThat(row.categoryId()).isEqualTo(unbudgetedId);
            assertThat(row.actual()).isEqualByComparingTo("12.00");
        });
        assertThat(response.hierarchy()).hasSize(2);
        BudgetCategoryProgressResponse parentNode = hierarchyNode(response, parentId);
        assertThat(parentNode.categoryId()).isEqualTo(parentId);
        assertThat(parentNode.allocationState()).isEqualTo(BudgetAllocationState.ALLOCATED);
        assertThat(parentNode.directTarget()).isEqualByComparingTo("40.00");
        assertThat(parentNode.rollupTarget()).isEqualByComparingTo("60.00");
        assertThat(parentNode.directActual()).isEqualByComparingTo("10.00");
        assertThat(parentNode.rollupActual()).isEqualByComparingTo("35.00");
        assertThat(parentNode.descendantAllocationCount()).isEqualTo(1);
        BudgetCategoryProgressResponse childNode = parentNode.children().getFirst();
        assertThat(childNode.allocationState()).isEqualTo(BudgetAllocationState.ALLOCATED);
        assertThat(childNode.directActual()).isEqualByComparingTo("0.00");
        assertThat(childNode.rollupActual()).isEqualByComparingTo("25.00");
        assertThat(childNode.children().getFirst().allocationState())
                .isEqualTo(BudgetAllocationState.COVERED_BY_ANCESTOR);
        assertThat(childNode.children().getFirst().directActual()).isEqualByComparingTo("25.00");
        assertThat(hierarchyNode(response, unbudgetedId).allocationState())
                .isEqualTo(BudgetAllocationState.UNBUDGETED);
    }

    @Test
    void rollsDeepSpendingIntoARootOnlyAllocationWithoutCountingItTwice() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        budget.addLine(parentId, amount("100.00"));
        TransactionCategory parent = category(parentId, null);
        TransactionCategory child = category(childId, parentId);
        TransactionCategory leaf = category(leafId, childId);
        when(leaf.getStatus()).thenReturn(CategoryStatus.ARCHIVED);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(parent, child, leaf));
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                allocation(UUID.randomUUID(), parentId, "10.00"),
                allocation(UUID.randomUUID(), leafId, "30.00")
        ));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.lines()).singleElement()
                .extracting(BudgetLineProgressResponse::actual).isEqualTo(amount("40.00"));
        assertThat(response.totalActual()).isEqualByComparingTo("40.00");
        BudgetCategoryProgressResponse root = response.hierarchy().getFirst();
        assertThat(root.rollupActual()).isEqualByComparingTo("40.00");
        assertThat(root.children().getFirst().allocationState())
                .isEqualTo(BudgetAllocationState.COVERED_BY_ANCESTOR);
        assertThat(root.children().getFirst().children().getFirst().allocationState())
                .isEqualTo(BudgetAllocationState.COVERED_BY_ANCESTOR);
        assertThat(root.children().getFirst().children().getFirst().categoryStatus())
                .isEqualTo(CategoryStatus.ARCHIVED);
        assertThat(root.children().getFirst().children().getFirst().directActual())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void returnsNullPercentageForAZeroPlannedLine() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        budget.addLine(parentId, amount("0.00"));
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        TransactionCategory parent = category(parentId, null);
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(parent));
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(allocation(UUID.randomUUID(), parentId, "3.00")));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.percentageUsed()).isNull();
        assertThat(response.lines().getFirst().percentageUsed()).isNull();
        assertThat(response.remaining()).isEqualByComparingTo("-3.00");
    }

    @Test
    void keepsRecurringCommitmentsSeparateFromActualsAndGroupsThemByBudgetLine() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        BudgetLine parentLine = budget.addLine(parentId, amount("50.00"));
        TransactionCategory parent = category(parentId, null);
        TransactionCategory child = category(childId, parentId);
        TransactionCategory unbudgeted = category(unbudgetedId, null);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(parent, child, unbudgeted));
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of());
        when(commitmentSource.findScheduledCommitments(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                commitment(childId, "Rent", "40.00", LocalDate.of(2026, 8, 1)),
                commitment(childId, "Streaming", "15.00", LocalDate.of(2026, 8, 5)),
                commitment(unbudgetedId, "Insurance", "20.00", LocalDate.of(2026, 8, 15))
        ));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.committed()).isEqualByComparingTo("75.00");
        assertThat(response.remainingAfterCommitments()).isEqualByComparingTo("-25.00");
        assertThat(response.underfunded()).isTrue();
        assertThat(response.totalActual()).isEqualByComparingTo("0.00");
        assertThat(response.lines()).singleElement().satisfies(line -> {
            assertThat(line.lineId()).isEqualTo(parentLine.getId());
            assertThat(line.committed()).isEqualByComparingTo("55.00");
            assertThat(line.remainingAfterCommitments()).isEqualByComparingTo("-5.00");
            assertThat(line.underfunded()).isTrue();
            assertThat(line.scheduledCommitments()).extracting(BudgetScheduledCommitment::name)
                    .containsExactly("Rent", "Streaming");
        });
        assertThat(response.unbudgetedCommitments()).singleElement().satisfies(row -> {
            assertThat(row.categoryId()).isEqualTo(unbudgetedId);
            assertThat(row.committed()).isEqualByComparingTo("20.00");
        });
        assertThat(hierarchyNode(response, parentId).rollupTarget()).isEqualByComparingTo("105.00");
        assertThat(hierarchyNode(response, parentId).children().getFirst().directScheduledTarget())
                .isEqualByComparingTo("55.00");
        assertThat(hierarchyNode(response, unbudgetedId).directScheduledTarget()).isEqualByComparingTo("20.00");
    }

    @Test
    void replacesASatisfiedScheduledTargetWithActualInProjectedUsage() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        budget.addLine(parentId, amount("800.00"));
        TransactionCategory parent = category(parentId, null);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(parent));
        UUID paidTransaction = UUID.randomUUID();
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(allocation(paidTransaction, parentId, "72.00")));
        UUID paidExpense = UUID.randomUUID();
        UUID outstandingExpense = UUID.randomUUID();
        when(commitmentSource.findScheduledCommitments(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                new BudgetScheduledCommitment(
                        paidExpense + ":2026-08-15", paidExpense, "Water",
                        LocalDate.of(2026, 8, 15), amount("80.00"), "USD", parentId, null,
                        true, amount("72.00"), amount("8.00"), paidTransaction),
                new BudgetScheduledCommitment(
                        outstandingExpense + ":2026-08-20", outstandingExpense, "Internet",
                        LocalDate.of(2026, 8, 20), amount("120.00"), "USD", parentId, null)
        ));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.planned()).isEqualByComparingTo("800.00");
        assertThat(response.scheduledTarget()).isEqualByComparingTo("200.00");
        assertThat(response.outstandingScheduledTarget()).isEqualByComparingTo("120.00");
        assertThat(response.totalBudgeted()).isEqualByComparingTo("1000.00");
        assertThat(response.totalActual()).isEqualByComparingTo("72.00");
        assertThat(response.percentSpent()).isEqualByComparingTo("7.20");
        assertThat(response.projectedUsage()).isEqualByComparingTo("192.00");
        assertThat(response.projectedRemaining()).isEqualByComparingTo("808.00");
        assertThat(response.projectedPercentage()).isEqualByComparingTo("19.20");
        assertThat(response.lines().getFirst().scheduledCommitments())
                .extracting(BudgetScheduledCommitment::satisfied)
                .containsExactly(true, false);
    }

    @Test
    void classifiesMatchedBillActualSeparatelyAndLeavesUnrelatedActivityUnplanned() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        TransactionCategory subscriptions = category(unbudgetedId, null);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(subscriptions));
        UUID billTransaction = UUID.randomUUID();
        UUID unrelatedTransaction = UUID.randomUUID();
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                allocation(billTransaction, unbudgetedId, "89.99"),
                allocation(unrelatedTransaction, unbudgetedId, "12.00")
        ));
        UUID recurringExpenseId = UUID.randomUUID();
        String occurrenceKey = recurringExpenseId + ":2026-08-31";
        when(commitmentSource.findScheduledCommitments(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(new BudgetScheduledCommitment(
                occurrenceKey, recurringExpenseId, "Home internet", LocalDate.of(2026, 8, 31),
                amount("100.00"), "USD", unbudgetedId, null,
                true, amount("89.99"), amount("10.01"), billTransaction
        )));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.flexibleActual()).isEqualByComparingTo("0.00");
        assertThat(response.billActual()).isEqualByComparingTo("89.99");
        assertThat(response.budgetedActual()).isEqualByComparingTo("89.99");
        assertThat(response.unbudgetedActual()).isEqualByComparingTo("12.00");
        assertThat(response.totalActual()).isEqualByComparingTo("101.99");
        assertThat(response.unbudgeted()).singleElement().satisfies(row -> {
            assertThat(row.actual()).isEqualByComparingTo("12.00");
            assertThat(row.drillDown().transactionIds()).containsExactly(unrelatedTransaction);
        });
        assertThat(response.unbudgetedCommitments()).singleElement().satisfies(row -> {
            assertThat(row.billActual()).isEqualByComparingTo("89.99");
            assertThat(row.actual()).isEqualByComparingTo("89.99");
        });
        assertThat(response.components()).singleElement().satisfies(component -> {
            assertThat(component.componentKey()).isEqualTo("occurrence:" + occurrenceKey);
            assertThat(component.source()).isEqualTo(BudgetComponentSource.RECURRING);
            assertThat(component.status()).isEqualTo(BudgetComponentStatus.SATISFIED);
            assertThat(component.target()).isEqualByComparingTo("100.00");
            assertThat(component.actual()).isEqualByComparingTo("89.99");
            assertThat(component.remaining()).isEqualByComparingTo("10.01");
            assertThat(component.percentageUsed()).isEqualByComparingTo("89.99");
            assertThat(component.variance()).isEqualByComparingTo("10.01");
            assertThat(component.linkedTransactionId()).isEqualTo(billTransaction);
            assertThat(component.drillDown().transactionIds()).containsExactly(billTransaction);
        });
    }

    @Test
    void exposesFlexibleAndMultipleRecurringComponentsIncludingZeroTargetsAndVariance() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        BudgetLine line = budget.addLine(parentId, amount("50.00"));
        TransactionCategory parent = category(parentId, null);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(parent));
        UUID flexibleTransaction = UUID.randomUUID();
        UUID paidTransaction = UUID.randomUUID();
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                allocation(flexibleTransaction, parentId, "5.00"),
                allocation(paidTransaction, parentId, "25.00")
        ));
        UUID paidId = UUID.randomUUID();
        when(commitmentSource.findScheduledCommitments(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of(
                commitment(parentId, "Free plan", "0.00", LocalDate.of(2026, 8, 1)),
                commitment(parentId, "Streaming", "10.00", LocalDate.of(2026, 8, 5)),
                new BudgetScheduledCommitment(
                        paidId + ":2026-08-20", paidId, "Insurance", LocalDate.of(2026, 8, 20),
                        amount("20.00"), "USD", parentId, null,
                        true, amount("25.00"), amount("-5.00"), paidTransaction)
        ));

        BudgetProgressResponse response = service.calculate(budgetId, null, null);

        assertThat(response.totalBudgeted()).isEqualByComparingTo("80.00");
        assertThat(response.flexibleActual()).isEqualByComparingTo("5.00");
        assertThat(response.billActual()).isEqualByComparingTo("25.00");
        assertThat(response.projectedUsage()).isEqualByComparingTo("40.00");
        assertThat(response.lines()).singleElement().satisfies(progress -> {
            assertThat(progress.lineId()).isEqualTo(line.getId());
            assertThat(progress.flexibleActual()).isEqualByComparingTo("5.00");
            assertThat(progress.billActual()).isEqualByComparingTo("25.00");
            assertThat(progress.actual()).isEqualByComparingTo("30.00");
        });
        assertThat(response.components()).hasSize(4);
        assertThat(response.components().getFirst().source()).isEqualTo(BudgetComponentSource.FLEXIBLE);
        assertThat(response.components().getFirst().actual()).isEqualByComparingTo("5.00");
        assertThat(response.components().stream()
                .filter(component -> component.target().signum() == 0)
                .findFirst().orElseThrow().percentageUsed()).isNull();
        assertThat(response.components()).filteredOn(component -> "Insurance".equals(component.name()))
                .singleElement().satisfies(component -> {
                    assertThat(component.actual()).isEqualByComparingTo("25.00");
                    assertThat(component.remaining()).isEqualByComparingTo("-5.00");
                    assertThat(component.percentageUsed()).isEqualByComparingTo("125.00");
                    assertThat(component.projectedUsage()).isEqualByComparingTo("25.00");
                });
    }

    @Test
    void rejectsAnIncompleteHierarchyRatherThanSilentlyDroppingANestedCategory() {
        Budget budget = new Budget(
                budgetId, ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        UUID missingParentId = UUID.randomUUID();
        TransactionCategory orphan = category(childId, missingParentId);
        when(budgetRepository.findByIdAndOwnerId(budgetId, ownerId)).thenReturn(Optional.of(budget));
        when(categoryRepository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId))
                .thenReturn(List.of(orphan));
        when(spendingSource.findExpenseAllocations(
                ownerId, "USD", budget.getStartDate(), budget.getEndDate(), null
        )).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.calculate(budgetId, null, null))
                .isInstanceOf(BudgetConflictException.class)
                .hasMessageContaining(missingParentId.toString());
    }

    private TransactionCategory category(UUID id, UUID parentId) {
        TransactionCategory category = mock(TransactionCategory.class);
        when(category.getId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        when(category.getName()).thenReturn(id.toString());
        when(category.getStatus()).thenReturn(CategoryStatus.ACTIVE);
        return category;
    }

    private BudgetCategoryProgressResponse hierarchyNode(BudgetProgressResponse response, UUID categoryId) {
        return response.hierarchy().stream()
                .filter(node -> node.categoryId().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }

    private BudgetSpendingAllocation allocation(UUID transactionId, UUID categoryId, String amount) {
        return new BudgetSpendingAllocation(transactionId, UUID.randomUUID(), categoryId, amount(amount));
    }

    private BudgetScheduledCommitment commitment(UUID categoryId, String name, String amount, LocalDate dueDate) {
        UUID recurringExpenseId = UUID.randomUUID();
        return new BudgetScheduledCommitment(
                recurringExpenseId + ":" + dueDate, recurringExpenseId, name, dueDate,
                amount(amount), "USD", categoryId, null
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
