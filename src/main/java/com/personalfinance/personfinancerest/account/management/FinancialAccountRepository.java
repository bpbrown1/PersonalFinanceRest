package com.personalfinance.personfinancerest.account.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    List<FinancialAccount> findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID ownerId);

    List<FinancialAccount> findAllByOwnerIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(UUID ownerId);

    Optional<FinancialAccount> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from FinancialAccount account where account.id = :id and account.ownerId = :ownerId")
    Optional<FinancialAccount> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
