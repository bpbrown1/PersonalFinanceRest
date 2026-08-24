package com.personalfinance.personfinancerest.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID>,
        JpaSpecificationExecutor<FinancialTransaction> {

    Optional<FinancialTransaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<FinancialTransaction> findAllByTransferIdAndOwnerIdOrderByType(UUID transferId, UUID ownerId);

    @Query("""
            select entry from FinancialTransaction entry
            where entry.ownerId = :ownerId and entry.transferId is not null
              and (:deleted is null or
                   (:deleted = false and entry.deletedAt is null) or
                   (:deleted = true and entry.deletedAt is not null))
            order by entry.transactionDate desc, entry.createdAt desc, entry.type
            """)
    List<FinancialTransaction> findTransferLegs(@Param("ownerId") UUID ownerId,
                                                @Param("deleted") Boolean deleted);

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
              and entry.type in (:incomeType, :expenseType)
              and (:accountId is null or entry.accountId = :accountId)
              and (:categoryId is null or entry.categoryId = :categoryId)
              and (:transactionType is null or entry.type = :transactionType)
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
            @Param("expenseType") TransactionType expenseType,
            @Param("accountId") UUID accountId,
            @Param("categoryId") UUID categoryId,
            @Param("transactionType") TransactionType transactionType
    );

    boolean existsByAccountId(UUID accountId);
}
