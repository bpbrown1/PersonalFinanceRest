package com.personalfinance.personfinancerest.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    List<FinancialTransaction> findAllByOwnerIdOrderByTransactionDateDescCreatedAtDesc(UUID ownerId);

    List<FinancialTransaction> findAllByOwnerIdAndDeletedAtIsNullOrderByTransactionDateDescCreatedAtDesc(UUID ownerId);

    List<FinancialTransaction> findAllByOwnerIdAndDeletedAtIsNotNullOrderByTransactionDateDescCreatedAtDesc(UUID ownerId);

    Optional<FinancialTransaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            select account.currency as currency,
                   sum(case when entry.type = :incomeType then entry.amount else 0 end) as income,
                   sum(case when entry.type = :expenseType then entry.amount else 0 end) as spending,
                   count(entry.id) as transactionCount
            from FinancialTransaction entry, FinancialAccount account
            where account.id = entry.accountId
              and account.ownerId = entry.ownerId
              and entry.ownerId = :ownerId
              and entry.deletedAt is null
              and (:fromDate is null or entry.transactionDate >= :fromDate)
              and (:toDate is null or entry.transactionDate <= :toDate)
            group by account.currency
            order by account.currency
            """)
    List<TransactionSummaryAggregate> summarize(
            @Param("ownerId") UUID ownerId,
            @Param("fromDate") LocalDate from,
            @Param("toDate") LocalDate to,
            @Param("incomeType") TransactionType incomeType,
            @Param("expenseType") TransactionType expenseType
    );

    boolean existsByAccountId(UUID accountId);
}
