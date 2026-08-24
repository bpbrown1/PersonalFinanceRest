package com.personalfinance.personfinancerest.transaction;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FinancialTransactionSpecifications {

    private FinancialTransactionSpecifications() {
    }

    static Specification<FinancialTransaction> matching(java.util.UUID ownerId,
                                                         TransactionSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("ownerId"), ownerId));
            switch (criteria.status()) {
                case ACTIVE -> predicates.add(builder.isNull(root.get("deletedAt")));
                case DELETED -> predicates.add(builder.isNotNull(root.get("deletedAt")));
                case ALL -> { }
            }
            if (criteria.accountId() != null) {
                predicates.add(builder.equal(root.get("accountId"), criteria.accountId()));
            }
            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("transactionDate"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("transactionDate"), criteria.to()));
            }
            if (criteria.categoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), criteria.categoryId()));
            }
            if (criteria.type() != null) {
                predicates.add(builder.equal(root.get("type"), criteria.type()));
            }
            if (criteria.minAmount() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), criteria.minAmount()));
            }
            if (criteria.maxAmount() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("amount"), criteria.maxAmount()));
            }
            if (criteria.text() != null) {
                String pattern = "%" + escapeLike(criteria.text().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        like(builder.lower(root.get("description")), pattern, builder),
                        like(builder.lower(root.get("merchantPayee")), pattern, builder),
                        like(builder.lower(root.get("notes")), pattern, builder),
                        like(builder.lower(root.get("externalReference")), pattern, builder)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate like(Expression<String> value, String pattern,
                                  jakarta.persistence.criteria.CriteriaBuilder builder) {
        return builder.like(value, pattern, '\\');
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
