package com.personalfinance.personfinancerest.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<FinancialAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
}
