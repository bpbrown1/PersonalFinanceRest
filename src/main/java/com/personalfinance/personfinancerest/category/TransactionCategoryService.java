package com.personalfinance.personfinancerest.category;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class TransactionCategoryService implements CategoryHierarchy {

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
        UUID categoryId = UUID.randomUUID();
        if (request.parentId() != null) {
            validateParent(ownerId, categoryId, request.parentId());
        }
        TransactionCategory category = new TransactionCategory(
                categoryId, ownerId, request.name(), request.applicability(), request.parentId()
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
    CategoryResponse updateParent(UUID categoryId, UpdateCategoryParentRequest request) {
        TransactionCategory category = findOwnedCategory(categoryId);
        if (request.parentId() != null) {
            validateParent(category.getOwnerId(), categoryId, request.parentId());
        }
        category.assignParent(request.parentId());
        return CategoryResponse.from(save(category));
    }

    @Transactional
    CategoryResponse archive(UUID categoryId) {
        TransactionCategory category = findOwnedCategory(categoryId);
        if (category.getStatus() == CategoryStatus.ACTIVE
                && repository.existsByOwnerIdAndParentIdAndArchivedAtIsNull(category.getOwnerId(), categoryId)) {
            throw new CategoryHierarchyConflictException(
                    "A category with active children cannot be archived: " + categoryId
            );
        }
        category.archive(Instant.now());
        return CategoryResponse.from(save(category));
    }

    @Transactional
    CategoryResponse restore(UUID categoryId) {
        TransactionCategory category = findOwnedCategory(categoryId);
        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            ensureActiveNameIsAvailable(category.getOwnerId(), category.getName(), categoryId);
            ensureParentIsActive(category);
        }
        category.restore();
        return CategoryResponse.from(save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> categoryAndDescendantIds(UUID ownerId, UUID rootCategoryId) {
        List<TransactionCategory> categories = repository.findAllByOwnerIdOrderByNormalizedNameAsc(ownerId);
        boolean rootExists = categories.stream().anyMatch(category -> category.getId().equals(rootCategoryId));
        if (!rootExists) {
            throw new CategoryNotFoundException(rootCategoryId);
        }

        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        categories.stream()
                .filter(category -> category.getParentId() != null)
                .forEach(category -> childrenByParent
                        .computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category.getId()));

        Set<UUID> result = new LinkedHashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        pending.add(rootCategoryId);
        while (!pending.isEmpty()) {
            UUID categoryId = pending.removeFirst();
            if (result.add(categoryId)) {
                pending.addAll(childrenByParent.getOrDefault(categoryId, List.of()));
            }
        }
        return Set.copyOf(result);
    }

    private TransactionCategory findOwnedCategory(UUID categoryId) {
        return repository.findByIdAndOwnerId(categoryId, currentUserProvider.userId())
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private void validateParent(UUID ownerId, UUID categoryId, UUID parentId) {
        if (categoryId.equals(parentId)) {
            throw new CategoryHierarchyConflictException("A category cannot be its own parent: " + categoryId);
        }

        TransactionCategory parent = repository.findByIdAndOwnerId(parentId, ownerId)
                .orElseThrow(() -> new CategoryNotFoundException(parentId));
        if (parent.getStatus() == CategoryStatus.ARCHIVED) {
            throw new CategoryHierarchyConflictException("An archived category cannot be assigned as a parent: " + parentId);
        }

        Set<UUID> visited = new HashSet<>();
        TransactionCategory ancestor = parent;
        while (ancestor != null) {
            if (!visited.add(ancestor.getId()) || ancestor.getId().equals(categoryId)) {
                throw new CategoryHierarchyConflictException(
                        "Category parent assignment would create a circular relationship: " + categoryId
                );
            }
            UUID ancestorId = ancestor.getParentId();
            ancestor = ancestorId == null ? null : repository.findByIdAndOwnerId(ancestorId, ownerId)
                    .orElseThrow(() -> new CategoryNotFoundException(ancestorId));
        }
    }

    private void ensureParentIsActive(TransactionCategory category) {
        if (category.getParentId() == null) {
            return;
        }
        TransactionCategory parent = repository.findByIdAndOwnerId(category.getParentId(), category.getOwnerId())
                .orElseThrow(() -> new CategoryNotFoundException(category.getParentId()));
        if (parent.getStatus() == CategoryStatus.ARCHIVED) {
            throw new CategoryHierarchyConflictException(
                    "A category cannot be restored while its parent is archived: " + category.getParentId()
            );
        }
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
