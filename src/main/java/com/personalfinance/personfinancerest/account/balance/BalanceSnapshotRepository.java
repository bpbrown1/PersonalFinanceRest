package com.personalfinance.personfinancerest.account.balance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, UUID> {

    List<BalanceSnapshot> findAllByAccountIdOrderByEffectiveAtAsc(UUID accountId);

    Optional<BalanceSnapshot> findFirstByAccountIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            UUID accountId, Instant effectiveAt);

    Optional<BalanceSnapshot> findByAccountIdAndSource(UUID accountId, BalanceSnapshotSource source);

    Optional<BalanceSnapshot> findByIdAndAccountId(UUID id, UUID accountId);

    boolean existsByAccountIdAndSource(UUID accountId, BalanceSnapshotSource source);

    boolean existsByAccountIdAndEffectiveAt(UUID accountId, Instant effectiveAt);
}
