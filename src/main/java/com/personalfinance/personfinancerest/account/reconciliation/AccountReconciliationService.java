package com.personalfinance.personfinancerest.account.reconciliation;

import com.personalfinance.personfinancerest.account.management.AccountStatus;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.CategoryApplicability;
import com.personalfinance.personfinancerest.category.CategoryNotFoundException;
import com.personalfinance.personfinancerest.category.CategoryStatus;
import com.personalfinance.personfinancerest.category.TransactionCategory;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.shared.money.MoneyValues;
import com.personalfinance.personfinancerest.transaction.FinancialTransaction;
import com.personalfinance.personfinancerest.transaction.FinancialTransactionRepository;
import com.personalfinance.personfinancerest.transaction.TransactionType;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
class AccountReconciliationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AccountReconciliationRepository reconciliationRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;

    AccountReconciliationService(AccountReconciliationRepository reconciliationRepository,
                                 FinancialAccountRepository accountRepository,
                                 FinancialTransactionRepository transactionRepository,
                                 TransactionCategoryRepository categoryRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.reconciliationRepository = reconciliationRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    ReconciliationPreviewResponse preview(UUID accountId, ReconciliationPreviewRequest request) {
        UUID ownerId = currentUserProvider.userId();
        FinancialAccount account = findOwnedAccount(accountId, ownerId);
        validateAccountAndDate(account, request.statementDate());
        BigDecimal statementBalance = money("statementBalance", request.statementBalance());
        LedgerState ledger = ledgerState(account, request.statementDate());
        BigDecimal difference = statementBalance.subtract(ledger.balance());
        return new ReconciliationPreviewResponse(
                accountId, account.getCurrency(), request.statementDate(), statementBalance,
                ledger.balance(), difference, adjustmentType(difference),
                ledger.transactions().size(), ledger.token()
        );
    }

    @Transactional
    ReconciliationResponse confirm(UUID accountId, String idempotencyKey,
                                   ReconciliationConfirmRequest request) {
        UUID ownerId = currentUserProvider.userId();
        String normalizedKey = validateIdempotencyKey(idempotencyKey);
        BigDecimal statementBalance = money("statementBalance", request.statementBalance());

        AccountReconciliation existing = reconciliationRepository
                .findByOwnerIdAndAccountIdAndIdempotencyKey(ownerId, accountId, normalizedKey)
                .orElse(null);
        if (existing != null) {
            ensureSameOperation(existing, request, statementBalance);
            return ReconciliationResponse.from(existing);
        }

        FinancialAccount account = accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId)
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
        existing = reconciliationRepository
                .findByOwnerIdAndAccountIdAndIdempotencyKey(ownerId, accountId, normalizedKey)
                .orElse(null);
        if (existing != null) {
            ensureSameOperation(existing, request, statementBalance);
            return ReconciliationResponse.from(existing);
        }
        validateAccountAndDate(account, request.statementDate());
        LedgerState ledger = ledgerState(account, request.statementDate());
        if (!ledger.token().equals(request.ledgerToken())) {
            throw new ReconciliationConflictException(
                    "The reconciliation preview is stale; refresh it before confirming"
            );
        }

        BigDecimal difference = statementBalance.subtract(ledger.balance());
        String explanation = normalizeExplanation(request.explanation(), difference);
        TransactionType type = adjustmentType(difference);
        TransactionCategory category = type == null
                ? null : validateCategory(request.categoryId(), ownerId, type);

        AccountReconciliation reconciliation = reconciliationRepository.saveAndFlush(
                new AccountReconciliation(
                        UUID.randomUUID(), ownerId, accountId, request.statementDate(),
                        statementBalance, ledger.balance(), difference, ledger.token(),
                        normalizedKey, explanation
                )
        );

        if (type != null) {
            FinancialTransaction adjustment = FinancialTransaction.reconciliationAdjustment(
                    UUID.randomUUID(), reconciliation.getId(), ownerId, accountId, category.getId(),
                    difference.abs(), type, request.statementDate(),
                    "Reconciliation adjustment for " + request.statementDate(), explanation
            );
            FinancialTransaction saved = transactionRepository.saveAndFlush(adjustment);
            reconciliation.attachTransaction(saved.getId());
            account.recordCurrentBalance(ledgerState(account, LocalDate.now(ZoneOffset.UTC)).balance());
            accountRepository.saveAndFlush(account);
            reconciliationRepository.saveAndFlush(reconciliation);
        }
        return ReconciliationResponse.from(reconciliation);
    }

    @Transactional(readOnly = true)
    ReconciliationPageResponse history(UUID accountId, int page, int size) {
        UUID ownerId = currentUserProvider.userId();
        findOwnedAccount(accountId, ownerId);
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidReconciliationRequestException(Map.of(
                    page < 0 ? "page" : "size",
                    page < 0 ? "Page must not be negative" : "Size must be between 1 and 100"
            ));
        }
        Page<AccountReconciliation> result = reconciliationRepository
                .findAllByOwnerIdAndAccountIdOrderByCreatedAtDesc(
                        ownerId, accountId, PageRequest.of(page, size)
                );
        return ReconciliationPageResponse.from(result);
    }

    private LedgerState ledgerState(FinancialAccount account, LocalDate statementDate) {
        List<FinancialTransaction> transactions = transactionRepository.findActiveLedgerAsOf(
                account.getOwnerId(), account.getId(), statementDate
        );
        BigDecimal balance = transactions.stream()
                .map(FinancialTransaction::balanceImpact)
                .reduce(account.getOpeningBalance(), BigDecimal::add)
                .setScale(2);
        return new LedgerState(balance, transactions, token(account, statementDate, transactions));
    }

    private String token(FinancialAccount account, LocalDate statementDate,
                         List<FinancialTransaction> transactions) {
        StringBuilder state = new StringBuilder()
                .append(account.getId()).append('|')
                .append(statementDate).append('|')
                .append(account.getOpeningDate()).append('|')
                .append(account.getOpeningBalance().toPlainString());
        transactions.forEach(transaction -> state.append('|')
                .append(transaction.getId()).append(':')
                .append(transaction.getTransactionDate()).append(':')
                .append(transaction.getType()).append(':')
                .append(transaction.getAmount().toPlainString()).append(':')
                .append(transaction.getUpdatedAt()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private FinancialAccount findOwnedAccount(UUID accountId, UUID ownerId) {
        return accountRepository.findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(() -> new FinancialAccountNotFoundException(accountId));
    }

    private void validateAccountAndDate(FinancialAccount account, LocalDate statementDate) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (account.getStatus() == AccountStatus.ARCHIVED) {
            errors.put("accountId", "An archived account cannot be reconciled");
        }
        if (statementDate.isBefore(account.getOpeningDate())) {
            errors.put("statementDate", "Statement date cannot precede the account opening date");
        } else if (statementDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            errors.put("statementDate", "Statement date cannot be in the future");
        }
        if (!errors.isEmpty()) {
            throw new InvalidReconciliationRequestException(errors);
        }
    }

    private TransactionCategory validateCategory(UUID categoryId, UUID ownerId, TransactionType type) {
        if (categoryId == null) {
            throw new InvalidReconciliationRequestException(Map.of(
                    "categoryId", "A category is required for a nonzero reconciliation adjustment"
            ));
        }
        TransactionCategory category = categoryRepository.findByIdAndOwnerId(categoryId, ownerId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            throw new InvalidReconciliationRequestException(Map.of(
                    "categoryId", "An archived category cannot be assigned"
            ));
        }
        boolean compatible = category.getApplicability() == CategoryApplicability.BOTH
                || category.getApplicability().name().equals(type.name());
        if (!compatible) {
            throw new InvalidReconciliationRequestException(Map.of(
                    "categoryId", "Category applicability is incompatible with the adjustment type"
            ));
        }
        return category;
    }

    private BigDecimal money(String field, BigDecimal value) {
        try {
            return MoneyValues.amountOrZero(value);
        } catch (ArithmeticException exception) {
            throw new InvalidReconciliationRequestException(Map.of(
                    field, "Amount must use at most two decimal places"
            ));
        }
    }

    private String validateIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new InvalidReconciliationRequestException(Map.of(
                    "Idempotency-Key", "Idempotency-Key must contain between 1 and 100 characters"
            ));
        }
        return value.trim();
    }

    private String normalizeExplanation(String value, BigDecimal difference) {
        if (difference.signum() == 0) {
            return value == null ? "" : value.trim();
        }
        if (value == null || value.isBlank()) {
            throw new InvalidReconciliationRequestException(Map.of(
                    "explanation", "An explanation is required for a nonzero reconciliation adjustment"
            ));
        }
        return value.trim();
    }

    private void ensureSameOperation(AccountReconciliation existing,
                                     ReconciliationConfirmRequest request,
                                     BigDecimal statementBalance) {
        String requestedExplanation = request.explanation() == null ? "" : request.explanation().trim();
        boolean sameCategory = existing.getTransactionId() == null
                || transactionRepository.findByReconciliationId(existing.getId())
                .map(transaction -> Objects.equals(transaction.getCategoryId(), request.categoryId()))
                .orElse(false);
        if (!existing.getStatementDate().equals(request.statementDate())
                || existing.getStatementBalance().compareTo(statementBalance) != 0
                || !existing.getLedgerToken().equals(request.ledgerToken())
                || !existing.getExplanation().equals(requestedExplanation)
                || !sameCategory) {
            throw new ReconciliationConflictException(
                    "Idempotency-Key was already used for a different reconciliation"
            );
        }
    }

    private TransactionType adjustmentType(BigDecimal difference) {
        if (difference.signum() > 0) {
            return TransactionType.INCOME;
        }
        if (difference.signum() < 0) {
            return TransactionType.EXPENSE;
        }
        return null;
    }

    private record LedgerState(
            BigDecimal balance,
            List<FinancialTransaction> transactions,
            String token
    ) {
        private LedgerState {
            transactions = List.copyOf(transactions);
            Objects.requireNonNull(token);
        }
    }
}
