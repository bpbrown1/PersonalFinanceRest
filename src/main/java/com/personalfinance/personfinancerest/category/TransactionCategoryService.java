package com.personalfinance.personfinancerest.category;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class TransactionCategoryService {

    private final TransactionCategoryRepository repository;
    private final CurrentUserProvider currentUserProvider;

    TransactionCategoryService(TransactionCategoryRepository repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    CategoryResponse create(CreateCategoryRequest request) {
        UUID ownerId = currentUserProvider.userId();
        ensureActiveNameIsAvailable(ownerId, request.name(), null);
        TransactionCategory category = new TransactionCategory(
                UUID.randomUUID(), ownerId, request.name(), request.applicability()
        );
        return CategoryResponse.from(save(category));
    }

    @Transactional(readOnly = true)
    List<CategoryResponse> findAll(CategoryStatusFilter status) {
        UUID ownerId = currentUserProvider.userId();
        List<TransactionCategory> categories = switch (status) {
            case ACTIVE -> repository.findAllByOwnerIdAndArchivedAtIsNullOrderByNormalizedNameAsc(ownerId);
            case ARCHIVED -> repository.findAllByOwnerIdAndArchivedAtIsNotNullOrderByNormalizedNameAsc(ownerId);
            case ALL -> repository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId);
        };
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    CategoryResponse findById(UUID categoryId) {
        return CategoryResponse.from(findOwnedCategory(categoryId));
    }

    @Transactional
    CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        TransactionCategory category = findOwnedCategory(categoryId);
        String name = request.name() == null ? category.getName() : request.name();
        if (request.name() != null && category.getStatus() == CategoryStatus.ACTIVE) {
            ensureActiveNameIsAvailable(category.getOwnerId(), name, categoryId);
        }
        category.update(
                name,
                request.applicability() == null ? category.getApplicability() : request.applicability()
        );
        return CategoryResponse.from(save(category));
    }

    @Transactional
    CategoryResponse archive(UUID categoryId) {
        TransactionCategory category = findOwnedCategory(categoryId);
        category.archive(Instant.now());
        return CategoryResponse.from(save(category));
    }

    @Transactional
    CategoryResponse restore(UUID categoryId) {
        TransactionCategory category = findOwnedCategory(categoryId);
        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            ensureActiveNameIsAvailable(category.getOwnerId(), category.getName(), categoryId);
        }
        category.restore();
        return CategoryResponse.from(save(category));
    }

    private TransactionCategory findOwnedCategory(UUID categoryId) {
        return repository.findByIdAndOwnerId(categoryId, currentUserProvider.userId())
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private void ensureActiveNameIsAvailable(UUID ownerId, String name, UUID excludedCategoryId) {
        String normalizedName = CategoryNames.normalizedName(name);
        boolean exists = excludedCategoryId == null
                ? repository.existsByOwnerIdAndActiveNameKey(ownerId, normalizedName)
                : repository.existsByOwnerIdAndActiveNameKeyAndIdNot(ownerId, normalizedName, excludedCategoryId);
        if (exists) {
            throw new DuplicateCategoryNameException(CategoryNames.displayName(name));
        }
    }

    private TransactionCategory save(TransactionCategory category) {
        try {
            return repository.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateCategoryNameException(category.getName());
        }
    }
}
