package com.personalfinance.personfinancerest.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Budget> findAllByOwnerIdAndArchivedAtIsNullOrderByStartDateDescNameAsc(UUID ownerId);

    List<Budget> findAllByOwnerIdAndArchivedAtIsNotNullOrderByStartDateDescNameAsc(UUID ownerId);

    List<Budget> findAllByOwnerIdOrderByStartDateDescNameAsc(UUID ownerId);
}
