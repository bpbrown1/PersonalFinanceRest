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
        JpaSpecificationExecutor<FinancialTransaction>, FinancialTransactionSummaryRepository {

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
            select distinct entry from FinancialTransaction entry
            left join fetch entry.splits
            where entry.ownerId = :ownerId
              and entry.deletedAt is null
              and entry.type = :expenseType
              and entry.transactionDate >= :fromDate
              and entry.transactionDate <= :toDate
              and entry.accountId in (
                  select account.id from FinancialAccount account
                  where account.ownerId = :ownerId
                    and account.currency = :currency
                    and (:accountId is null or account.id = :accountId)
              )
            order by entry.transactionDate, entry.createdAt, entry.id
            """)
    List<FinancialTransaction> findBudgetExpenses(
            @Param("ownerId") UUID ownerId,
            @Param("currency") String currency,
            @Param("fromDate") LocalDate from,
            @Param("toDate") LocalDate to,
            @Param("accountId") UUID accountId,
            @Param("expenseType") TransactionType expenseType
    );

    boolean existsByAccountId(UUID accountId);
}
