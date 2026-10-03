package com.personalfinance.personfinancerest.account.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AccountReconciliationRepository extends JpaRepository<AccountReconciliation, UUID> {

    Optional<AccountReconciliation> findByOwnerIdAndAccountIdAndIdempotencyKey(
            UUID ownerId, UUID accountId, String idempotencyKey
    );

    Page<AccountReconciliation> findAllByOwnerIdAndAccountIdOrderByCreatedAtDesc(
            UUID ownerId, UUID accountId, Pageable pageable
    );
}
