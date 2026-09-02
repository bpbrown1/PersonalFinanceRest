package com.personalfinance.personfinancerest.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class FinancialTransactionSummaryRepositoryImpl implements FinancialTransactionSummaryRepository {

    private static final String BASE_QUERY = """
            select account.currency,
                   sum(case when entry.type = :incomeType then coalesce(split.amount, entry.amount) else 0 end),
                   sum(case when entry.type = :expenseType then coalesce(split.amount, entry.amount) else 0 end),
                   count(distinct entry.id)
            from FinancialTransaction entry, FinancialAccount account
            left join entry.splits split
            where account.id = entry.accountId
              and account.ownerId = entry.ownerId
              and entry.ownerId = :ownerId
              and entry.deletedAt is null
              and entry.type in (:incomeType, :expenseType)
            """;

    private final EntityManager entityManager;

    FinancialTransactionSummaryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<TransactionSummaryAggregate> summarize(
            UUID ownerId,
            LocalDate from,
            LocalDate to,
            TransactionType incomeType,
            TransactionType expenseType,
            UUID accountId,
            UUID categoryId,
            TransactionType transactionType
    ) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                queryText(from, to, accountId, categoryId, transactionType), Object[].class
        );
        query.setParameter("ownerId", ownerId);
        query.setParameter("incomeType", incomeType);
        query.setParameter("expenseType", expenseType);
        setOptionalParameters(query, from, to, accountId, categoryId, transactionType);
        return query.getResultList().stream()
                .map(row -> new SummaryRow(
                        (String) row[0], (BigDecimal) row[1], (BigDecimal) row[2], ((Number) row[3]).longValue()
                ))
                .map(TransactionSummaryAggregate.class::cast)
                .toList();
    }

    static String queryText(LocalDate from, LocalDate to, UUID accountId, UUID categoryId,
                            TransactionType transactionType) {
        StringBuilder query = new StringBuilder(BASE_QUERY);
        if (accountId != null) {
            query.append(" and entry.accountId = :accountId");
        }
        if (categoryId != null) {
            query.append(" and ((split.id is null and entry.categoryId = :categoryId)")
                    .append(" or split.categoryId = :categoryId)");
        }
        if (transactionType != null) {
            query.append(" and entry.type = :transactionType");
        }
        if (from != null) {
            query.append(" and entry.transactionDate >= :fromDate");
        }
        if (to != null) {
            query.append(" and entry.transactionDate <= :toDate");
        }
        return query.append(" group by account.currency order by account.currency").toString();
    }

    private void setOptionalParameters(TypedQuery<Object[]> query, LocalDate from, LocalDate to,
                                       UUID accountId, UUID categoryId, TransactionType transactionType) {
        if (accountId != null) {
            query.setParameter("accountId", accountId);
        }
        if (categoryId != null) {
            query.setParameter("categoryId", categoryId);
        }
        if (transactionType != null) {
            query.setParameter("transactionType", transactionType);
        }
        if (from != null) {
            query.setParameter("fromDate", from);
        }
        if (to != null) {
            query.setParameter("toDate", to);
        }
    }

    private record SummaryRow(
            String currency,
            BigDecimal income,
            BigDecimal spending,
            long transactionCount
    ) implements TransactionSummaryAggregate {

        @Override
        public String getCurrency() {
            return currency;
        }

        @Override
        public BigDecimal getIncome() {
            return income;
        }

        @Override
        public BigDecimal getSpending() {
            return spending;
        }

        @Override
        public long getTransactionCount() {
            return transactionCount;
        }
    }
}
