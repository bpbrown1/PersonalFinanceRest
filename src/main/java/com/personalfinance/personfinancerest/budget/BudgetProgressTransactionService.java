package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.category.CategoryNotFoundException;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.transaction.TransactionPageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
class BudgetProgressTransactionService {

    private final BudgetProgressService progressService;
    private final TransactionCategoryRepository categoryRepository;
    private final BudgetProgressTransactionPageSource transactionPageSource;

    BudgetProgressTransactionService(
            BudgetProgressService progressService,
            TransactionCategoryRepository categoryRepository,
            BudgetProgressTransactionPageSource transactionPageSource
    ) {
        this.progressService = progressService;
        this.categoryRepository = categoryRepository;
        this.transactionPageSource = transactionPageSource;
    }

    @Transactional(readOnly = true)
    TransactionPageResponse findPage(UUID budgetId, BudgetProgressTransactionQuery query) {
        BudgetProgressResponse progress = switch (query.scope()) {
            case OVERALL -> progressService.calculate(budgetId, query.accountId(), query.categoryId());
            case LINE, UNBUDGETED -> progressService.calculate(budgetId, query.accountId(), null);
        };

        Set<UUID> transactionIds = switch (query.scope()) {
            case OVERALL -> Set.copyOf(progress.drillDown().transactionIds());
            case LINE -> progress.lines().stream()
                    .filter(line -> line.lineId().equals(query.lineId()))
                    .findFirst()
                    .map(line -> Set.copyOf(line.drillDown().transactionIds()))
                    .orElseThrow(() -> new BudgetLineNotFoundException(query.lineId()));
            case UNBUDGETED -> unbudgetedTransactionIds(progress, query);
        };

        return transactionPageSource.findPage(
                progress.ownerId(), transactionIds,
                query.page(), query.size(), query.sort(), query.direction()
        );
    }

    private Set<UUID> unbudgetedTransactionIds(
            BudgetProgressResponse progress,
            BudgetProgressTransactionQuery query
    ) {
        if (!query.uncategorized()) {
            categoryRepository.findByIdAndOwnerId(query.categoryId(), progress.ownerId())
                    .orElseThrow(() -> new CategoryNotFoundException(query.categoryId()));
        }
        UUID categoryId = query.uncategorized() ? null : query.categoryId();
        return progress.unbudgeted().stream()
                .filter(row -> java.util.Objects.equals(row.categoryId(), categoryId))
                .findFirst()
                .map(row -> Set.copyOf(row.drillDown().transactionIds()))
                .orElseGet(Set::of);
    }
}
