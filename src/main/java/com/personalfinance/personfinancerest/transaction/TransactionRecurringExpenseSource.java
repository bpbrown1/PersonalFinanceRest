package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseTransactionCandidate;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseTransactionSource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
class TransactionRecurringExpenseSource implements RecurringExpenseTransactionSource {

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialAccountRepository accountRepository;

    TransactionRecurringExpenseSource(FinancialTransactionRepository transactionRepository,
                                      FinancialAccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public RecurringExpenseTransactionCandidate findOwned(UUID ownerId, UUID transactionId) {
        FinancialTransaction transaction = transactionRepository.findByIdAndOwnerId(transactionId, ownerId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        return candidate(transaction);
    }

    @Override
    public Map<UUID, RecurringExpenseTransactionCandidate> findOwned(
            UUID ownerId, Collection<UUID> transactionIds) {
        Map<UUID, RecurringExpenseTransactionCandidate> result = new LinkedHashMap<>();
        transactionRepository.findAllById(transactionIds).stream()
                .filter(transaction -> transaction.getOwnerId().equals(ownerId))
                .forEach(transaction -> result.put(transaction.getId(), candidate(transaction)));
        return Map.copyOf(result);
    }

    private RecurringExpenseTransactionCandidate candidate(FinancialTransaction transaction) {
        String currency = accountRepository.findByIdAndOwnerId(transaction.getAccountId(), transaction.getOwnerId())
                .orElseThrow(() -> new FinancialAccountNotFoundException(transaction.getAccountId()))
                .getCurrency();
        return new RecurringExpenseTransactionCandidate(
                transaction.getId(), transaction.getOwnerId(), transaction.getAccountId(),
                transaction.getCategoryId(), transaction.getAmount(), currency,
                transaction.getTransactionDate(), transaction.getDescription(),
                transaction.getType() == TransactionType.EXPENSE,
                transaction.getStatus() == TransactionStatus.ACTIVE,
                !transaction.getSplits().isEmpty()
        );
    }
}
