package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.category.CategoryApplicability;
import com.personalfinance.personfinancerest.category.CategoryNotFoundException;
import com.personalfinance.personfinancerest.category.CategoryStatus;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BudgetCopyServiceTest {

    private final BudgetRepository repository = mock(BudgetRepository.class);
    private final TransactionCategoryRepository categories = mock(TransactionCategoryRepository.class);
    private final CurrentUserProvider user = mock(CurrentUserProvider.class);
    private final BudgetWriteLock writeLock = mock(BudgetWriteLock.class);
    private final BudgetService service = new BudgetService(repository, categories, user, writeLock);
    private final UUID ownerId = UUID.randomUUID();
    private final CopyBudgetRequest request = new CopyBudgetRequest("2028-02");

    @BeforeEach
    void setUp() {
        when(user.userId()).thenReturn(ownerId);
    }

    @Test
    void copiesOnlyActiveLinesInOrderWithNewIdentitiesAndFreshLifecycle() {
        Budget source = source();
        BudgetLine first = source.addLine(eligibleCategory(), new BigDecimal("1200.25"));
        BudgetLine archived = source.addLine(UUID.randomUUID(), new BigDecimal("10.00"));
        BudgetLine last = source.addLine(eligibleCategory(), new BigDecimal("0.00"));
        source.reorder(List.of(last.getId(), archived.getId(), first.getId()));
        archived.archive(Instant.parse("2020-01-01T00:00:00Z"));
        source.archive(Instant.parse("2020-02-01T00:00:00Z"));
        when(repository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse response = service.copy(source.getId(), request);

        assertThat(response.id()).isNotEqualTo(source.getId());
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.name()).isEqualTo(source.getName());
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(response.status()).isEqualTo(BudgetStatus.ACTIVE);
        assertThat(response.archivedAt()).isNull();
        assertThat(response.version()).isZero();
        assertThat(response.totalPlanned()).isEqualTo(new BigDecimal("1200.25"));
        assertThat(response.lines()).extracting(BudgetLineResponse::categoryId)
                .containsExactly(last.getCategoryId(), first.getCategoryId());
        assertThat(response.lines()).extracting(BudgetLineResponse::position).containsExactly(0, 1);
        assertThat(response.lines()).extracting(BudgetLineResponse::id)
                .doesNotContain(first.getId(), last.getId(), archived.getId());
        verify(categories, never()).findByIdAndOwnerId(archived.getCategoryId(), ownerId);

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(repository).saveAndFlush(saved.capture());
        saved.getValue().getLines().get(0).replace(last.getCategoryId(), new BigDecimal("99.00"));
        assertThat(last.getPlannedAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(source.getStatus()).isEqualTo(BudgetStatus.ARCHIVED);
        assertThat(source.getLines()).hasSize(3);
        var ordering = inOrder(writeLock, repository);
        ordering.verify(writeLock).acquire(ownerId);
        ordering.verify(repository).findByIdAndOwnerId(source.getId(), ownerId);
        ordering.verify(repository).findFirstByOwnerIdAndStartDateOrderByCreatedAtAscIdAsc(
                ownerId, request.month().atDay(1));
    }

    @Test
    void reportsExistingBudgetWithoutValidatingOrSavingLines() {
        Budget source = source();
        Budget existing = new Budget(UUID.randomUUID(), ownerId, "Existing", "EUR",
                request.month().atDay(1), request.month().atEndOfMonth());
        existing.archive(Instant.now());
        when(repository.findFirstByOwnerIdAndStartDateOrderByCreatedAtAscIdAsc(ownerId, request.month().atDay(1)))
                .thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.copy(source.getId(), request))
                .isInstanceOfSatisfying(BudgetTargetMonthConflictException.class,
                        exception -> assertThat(exception.getExistingBudgetId()).isEqualTo(existing.getId()));
        verifyNoInteractions(categories);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownOrForeignSourceBeforeInspectingTargetMonth() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.copy(missing, request)).isInstanceOf(BudgetNotFoundException.class);
        verify(repository, never()).findFirstByOwnerIdAndStartDateOrderByCreatedAtAscIdAsc(any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnArchivedCategoryWithoutPartiallySaving() {
        Budget source = source();
        source.addLine(eligibleCategory(), new BigDecimal("1.00"));
        UUID categoryId = eligibleCategory();
        when(categories.findByIdAndOwnerId(categoryId, ownerId).orElseThrow().getStatus())
                .thenReturn(CategoryStatus.ARCHIVED);
        source.addLine(categoryId, new BigDecimal("2.00"));
        assertThatThrownBy(() -> service.copy(source.getId(), request)).isInstanceOf(BudgetConflictException.class);
        verify(repository, never()).saveAndFlush(any());
        assertThat(source.getLines()).hasSize(2);
    }

    @Test
    void rejectsIncomeOnlyCategories() {
        Budget source = source();
        UUID id = UUID.randomUUID();
        TransactionCategory category = mock(TransactionCategory.class);
        when(category.getStatus()).thenReturn(CategoryStatus.ACTIVE);
        when(category.getApplicability()).thenReturn(CategoryApplicability.INCOME);
        when(categories.findByIdAndOwnerId(id, ownerId)).thenReturn(Optional.of(category));
        source.addLine(id, new BigDecimal("2.00"));
        assertThatThrownBy(() -> service.copy(source.getId(), request)).isInstanceOf(BudgetConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingOrForeignCategoryReferences() {
        Budget source = source();
        source.addLine(UUID.randomUUID(), new BigDecimal("1.00"));
        assertThatThrownBy(() -> service.copy(source.getId(), request)).isInstanceOf(CategoryNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void copiesAnEmptyBudget() {
        Budget source = source();
        when(repository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BudgetResponse copied = service.copy(source.getId(), request);
        assertThat(copied.lines()).isEmpty();
        assertThat(copied.totalPlanned()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void usesReviewedLinesAsCompleteOrderedTargetWithoutInspectingOmittedSourceLines() {
        Budget source = source();
        UUID omittedArchivedCategory = UUID.randomUUID();
        source.addLine(omittedArchivedCategory, new BigDecimal("50.00"));
        UUID added = eligibleCategory();
        UUID retained = eligibleCategory();
        source.addLine(retained, new BigDecimal("100.00"));
        CopyBudgetRequest reviewed = new CopyBudgetRequest("2028-02", List.of(
                new CreateBudgetLineRequest(retained, new BigDecimal("125.50")),
                new CreateBudgetLineRequest(added, new BigDecimal("25.00"))
        ));
        when(repository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse copied = service.copy(source.getId(), reviewed);

        assertThat(copied.lines()).extracting(BudgetLineResponse::categoryId)
                .containsExactly(retained, added);
        assertThat(copied.lines()).extracting(BudgetLineResponse::plannedAmount)
                .containsExactly(new BigDecimal("125.50"), new BigDecimal("25.00"));
        assertThat(copied.lines()).extracting(BudgetLineResponse::position).containsExactly(0, 1);
        assertThat(copied.totalPlanned()).isEqualTo(new BigDecimal("150.50"));
        verify(categories, never()).findByIdAndOwnerId(omittedArchivedCategory, ownerId);
        assertThat(source.getLines()).extracting(BudgetLine::getPlannedAmount)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("100.00"));
    }

    @Test
    void explicitEmptyReviewedLinesCreateAnEmptyTarget() {
        Budget source = source();
        source.addLine(eligibleCategory(), new BigDecimal("100.00"));
        when(repository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse copied = service.copy(source.getId(), new CopyBudgetRequest("2028-02", List.of()));

        assertThat(copied.lines()).isEmpty();
        assertThat(copied.totalPlanned()).isEqualTo(new BigDecimal("0.00"));
        verifyNoInteractions(categories);
    }

    @Test
    void rejectsDuplicateReviewedCategoriesBeforeSaving() {
        Budget source = source();
        UUID category = UUID.randomUUID();
        CopyBudgetRequest reviewed = new CopyBudgetRequest("2028-02", List.of(
                new CreateBudgetLineRequest(category, BigDecimal.ONE),
                new CreateBudgetLineRequest(category, BigDecimal.TEN)
        ));

        assertThatThrownBy(() -> service.copy(source.getId(), reviewed))
                .isInstanceOf(InvalidBudgetRequestException.class);
        verifyNoInteractions(categories);
        verify(repository, never()).saveAndFlush(any());
    }

    private Budget source() {
        Budget source = new Budget(UUID.randomUUID(), ownerId, "Monthly plan", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        when(repository.findByIdAndOwnerId(source.getId(), ownerId)).thenReturn(Optional.of(source));
        return source;
    }

    private UUID eligibleCategory() {
        UUID id = UUID.randomUUID();
        TransactionCategory category = mock(TransactionCategory.class);
        when(category.getStatus()).thenReturn(CategoryStatus.ACTIVE);
        when(category.getApplicability()).thenReturn(CategoryApplicability.BOTH);
        when(categories.findByIdAndOwnerId(id, ownerId)).thenReturn(Optional.of(category));
        return id;
    }
}
