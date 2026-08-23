package com.personalfinance.personfinancerest.account.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    List<FinancialAccount> findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID ownerId);

    List<FinancialAccount> findAllByOwnerIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(UUID ownerId);

    Optional<FinancialAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
}
