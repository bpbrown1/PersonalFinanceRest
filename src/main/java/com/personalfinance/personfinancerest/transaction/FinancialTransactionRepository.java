package com.personalfinance.personfinancerest.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    List<FinancialTransaction> findAllByOwnerIdOrderByTransactionDateDescCreatedAtDesc(UUID ownerId);

    List<FinancialTransaction> findAllByOwnerIdAndDeletedAtIsNullOrderByTransactionDateDescCreatedAtDesc(UUID ownerId);

    List<FinancialTransaction> findAllByOwnerIdAndDeletedAtIsNotNullOrderByTransactionDateDescCreatedAtDesc(UUID ownerId);

    Optional<FinancialTransaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByAccountId(UUID accountId);
}
