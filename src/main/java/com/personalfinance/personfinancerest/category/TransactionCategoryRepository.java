package com.personalfinance.personfinancerest.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, UUID> {

    List<TransactionCategory> findAllByOwnerIdOrderByNormalizedNameAsc(UUID ownerId);

    List<TransactionCategory> findAllByOwnerIdAndArchivedAtIsNullOrderByNormalizedNameAsc(UUID ownerId);

    List<TransactionCategory> findAllByOwnerIdAndArchivedAtIsNotNullOrderByNormalizedNameAsc(UUID ownerId);

    Optional<TransactionCategory> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndActiveNameKey(UUID ownerId, String activeNameKey);

    boolean existsByOwnerIdAndActiveNameKeyAndIdNot(UUID ownerId, String activeNameKey, UUID id);
}
