package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
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

    private TransactionCategory category(UUID id, UUID parentId) {
        TransactionCategory category = mock(TransactionCategory.class);
        when(category.getId()).thenReturn(id);
        when(category.getParentId()).thenReturn(parentId);
        return category;
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
