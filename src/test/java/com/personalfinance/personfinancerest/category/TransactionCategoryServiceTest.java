package com.personalfinance.personfinancerest.category;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionCategoryServiceTest {

    @Mock
    private TransactionCategoryRepository repository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private TransactionCategoryService service;
    private UUID ownerId;
    private UUID categoryId;
    private TransactionCategory category;

    @BeforeEach
    void setUp() {
        service = new TransactionCategoryService(repository, currentUserProvider);
        ownerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        category = new TransactionCategory(categoryId, ownerId, "Groceries", CategoryApplicability.EXPENSE);
    }

    @Test
    void createsANormalizedCategoryForTheCurrentOwner() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.saveAndFlush(any(TransactionCategory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.create(
                new CreateCategoryRequest("  Food   and Dining  ", CategoryApplicability.EXPENSE)
        );

        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.name()).isEqualTo("Food and Dining");
        assertThat(response.applicability()).isEqualTo(CategoryApplicability.EXPENSE);
        verify(repository).existsByOwnerIdAndActiveNameKey(ownerId, "food and dining");
    }

    @Test
    void rejectsAnExistingActiveNameBeforeSaving() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.existsByOwnerIdAndActiveNameKey(ownerId, "groceries")).willReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateCategoryRequest(" GROCERIES ", CategoryApplicability.EXPENSE)
        )).isInstanceOf(DuplicateCategoryNameException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void translatesARaceAtTheDatabaseConstraintIntoAConflict() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.saveAndFlush(any(TransactionCategory.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> service.create(
                new CreateCategoryRequest("Groceries", CategoryApplicability.EXPENSE)
        )).isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    void selectsTheRequestedOwnerScopedList() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findAllByOwnerIdAndArchivedAtIsNullOrderByNormalizedNameAsc(ownerId))
                .willReturn(List.of(category));
        given(repository.findAllByOwnerIdAndArchivedAtIsNotNullOrderByNormalizedNameAsc(ownerId))
                .willReturn(List.of(category));
        given(repository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId)).willReturn(List.of(category));

        assertThat(service.findAll(CategoryStatusFilter.ACTIVE)).hasSize(1);
        assertThat(service.findAll(CategoryStatusFilter.ARCHIVED)).hasSize(1);
        assertThat(service.findAll(CategoryStatusFilter.ALL)).hasSize(1);
    }

    @Test
    void updatesOnlyTheSuppliedFieldsAndExcludesItselfFromDuplicateDetection() {
        givenOwnedCategory();
        given(repository.saveAndFlush(category)).willReturn(category);

        CategoryResponse response = service.update(categoryId, new UpdateCategoryRequest("  Food  ", null));

        assertThat(response.name()).isEqualTo("Food");
        assertThat(response.applicability()).isEqualTo(CategoryApplicability.EXPENSE);
        verify(repository).existsByOwnerIdAndActiveNameKeyAndIdNot(ownerId, "food", categoryId);
    }

    @Test
    void updatesAnArchivedCategoryNameWithoutReservingIt() {
        category.archive(java.time.Instant.now());
        givenOwnedCategory();
        given(repository.saveAndFlush(category)).willReturn(category);

        CategoryResponse response = service.update(
                categoryId, new UpdateCategoryRequest("Dining", CategoryApplicability.BOTH)
        );

        assertThat(response.name()).isEqualTo("Dining");
        assertThat(response.status()).isEqualTo(CategoryStatus.ARCHIVED);
        verify(repository, never()).existsByOwnerIdAndActiveNameKeyAndIdNot(any(), any(), any());
    }

    @Test
    void archivesAndRestoresIdempotently() {
        givenOwnedCategory();
        given(repository.saveAndFlush(category)).willReturn(category);

        assertThat(service.archive(categoryId).status()).isEqualTo(CategoryStatus.ARCHIVED);
        assertThat(service.archive(categoryId).status()).isEqualTo(CategoryStatus.ARCHIVED);
        assertThat(service.restore(categoryId).status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(service.restore(categoryId).status()).isEqualTo(CategoryStatus.ACTIVE);

        verify(repository, times(4)).findByIdAndOwnerId(categoryId, ownerId);
        verify(repository, times(1))
                .existsByOwnerIdAndActiveNameKeyAndIdNot(ownerId, "groceries", categoryId);
    }

    @Test
    void rejectsRestoreWhenTheActiveNameWasReused() {
        category.archive(java.time.Instant.now());
        givenOwnedCategory();
        given(repository.existsByOwnerIdAndActiveNameKeyAndIdNot(ownerId, "groceries", categoryId))
                .willReturn(true);

        assertThatThrownBy(() -> service.restore(categoryId))
                .isInstanceOf(DuplicateCategoryNameException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reportsAnUnknownOrUnownedCategoryAsNotFound() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findByIdAndOwnerId(categoryId, ownerId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(categoryId))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining(categoryId.toString());
    }

    private void givenOwnedCategory() {
        given(currentUserProvider.userId()).willReturn(ownerId);
        given(repository.findByIdAndOwnerId(categoryId, ownerId)).willReturn(Optional.of(category));
    }
}
