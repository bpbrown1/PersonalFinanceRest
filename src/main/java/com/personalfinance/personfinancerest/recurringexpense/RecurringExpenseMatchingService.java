package com.personalfinance.personfinancerest.recurringexpense;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecurringExpenseMatchingService {

    private final RecurringExpenseMatchRepository matchRepository;
    private final RecurringExpenseRepository expenseRepository;
    private final RecurringExpenseOccurrenceProjector projector;
    private final RecurringExpenseTransactionSource transactionSource;
    private final CurrentUserProvider currentUserProvider;

    RecurringExpenseMatchingService(RecurringExpenseMatchRepository matchRepository,
                                    RecurringExpenseRepository expenseRepository,
                                    RecurringExpenseOccurrenceProjector projector,
                                    RecurringExpenseTransactionSource transactionSource,
                                    CurrentUserProvider currentUserProvider) {
        this.matchRepository = matchRepository;
        this.expenseRepository = expenseRepository;
        this.projector = projector;
        this.transactionSource = transactionSource;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public RecurringExpenseOccurrenceReference applyTransactionSelection(
            RecurringExpenseTransactionCandidate transaction,
            RecurringExpenseOccurrenceSelection selection,
            boolean replace) {
        UUID ownerId = transaction.ownerId();
        if (selection == null) {
            matchRepository.findByOwnerIdAndTransactionId(ownerId, transaction.id())
                    .ifPresent(matchRepository::delete);
            return null;
        }
        return RecurringExpenseOccurrenceReference.from(match(
                ownerId, selection.recurringExpenseId(), selection.dueDate(),
                transaction, replace
        ));
    }

    @Transactional
    RecurringExpenseOccurrenceResponse link(UUID recurringExpenseId, LocalDate dueDate,
                                            UUID transactionId, boolean replace) {
        UUID ownerId = currentUserProvider.userId();
        RecurringExpenseTransactionCandidate transaction = transactionSource.findOwned(ownerId, transactionId);
        match(ownerId, recurringExpenseId, dueDate, transaction, replace);
        return occurrence(ownerId, recurringExpenseId, dueDate);
    }

    @Transactional
    RecurringExpenseOccurrenceResponse unlink(UUID recurringExpenseId, LocalDate dueDate) {
        UUID ownerId = currentUserProvider.userId();
        RecurringExpense expense = findOwned(ownerId, recurringExpenseId);
        ensureOccurrence(expense, dueDate);
        matchRepository.findByOwnerIdAndRecurringExpenseIdAndDueDate(ownerId, recurringExpenseId, dueDate)
                .ifPresent(matchRepository::delete);
        return response(expense, dueDate, null);
    }

    @Transactional(readOnly = true)
    public RecurringExpenseOccurrenceReference referenceForTransaction(UUID ownerId, UUID transactionId) {
        return matchRepository.findByOwnerIdAndTransactionId(ownerId, transactionId)
                .map(RecurringExpenseOccurrenceReference::from).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, RecurringExpenseOccurrenceReference> referencesForTransactions(
            UUID ownerId, Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RecurringExpenseOccurrenceReference> result = new HashMap<>();
        matchRepository.findAllByOwnerIdAndTransactionIdIn(ownerId, transactionIds)
                .forEach(match -> result.put(match.getTransactionId(),
                        RecurringExpenseOccurrenceReference.from(match)));
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public void validateRestore(RecurringExpenseTransactionCandidate transaction) {
        Optional<RecurringExpenseMatch> retained = matchRepository.findByOwnerIdAndTransactionId(
                transaction.ownerId(), transaction.id());
        if (retained.isEmpty()) {
            return;
        }
        RecurringExpenseMatch match = retained.get();
        RecurringExpense expense = findOwned(transaction.ownerId(), match.getRecurringExpenseId());
        if (expense.getStatus() != RecurringExpenseStatus.ACTIVE) {
            throw new RecurringExpenseConflictException(
                    "A transaction matched to an archived recurring expense cannot be restored"
            );
        }
        ensureOccurrence(expense, match.getDueDate());
        validateCandidate(expense, transaction, false);
    }

    @Transactional(readOnly = true)
    void validateExistingMatches(RecurringExpense expense) {
        List<RecurringExpenseMatch> matches = matchRepository.findAllByOwnerIdAndRecurringExpenseId(
                expense.getOwnerId(), expense.getId());
        if (matches.isEmpty()) {
            return;
        }
        Map<UUID, RecurringExpenseTransactionCandidate> transactions = transactionSource.findOwned(
                expense.getOwnerId(), matches.stream().map(RecurringExpenseMatch::getTransactionId).toList());
        for (RecurringExpenseMatch match : matches) {
            ensureOccurrence(expense, match.getDueDate());
            RecurringExpenseTransactionCandidate transaction = transactions.get(match.getTransactionId());
            if (transaction == null) {
                throw new RecurringExpenseConflictException("A retained recurring expense match is invalid");
            }
            validateCandidate(expense, transaction, false);
        }
    }

    @Transactional(readOnly = true)
    Map<OccurrenceIdentity, MatchDetails> matches(UUID ownerId, Collection<UUID> recurringExpenseIds,
                                                  LocalDate from, LocalDate to) {
        if (recurringExpenseIds.isEmpty()) {
            return Map.of();
        }
        List<RecurringExpenseMatch> matches = matchRepository
                .findAllByOwnerIdAndRecurringExpenseIdInAndDueDateBetween(
                        ownerId, recurringExpenseIds, from, to);
        Map<UUID, RecurringExpenseTransactionCandidate> transactions = transactionSource.findOwned(
                ownerId, matches.stream().map(RecurringExpenseMatch::getTransactionId).toList());
        Map<OccurrenceIdentity, MatchDetails> result = new LinkedHashMap<>();
        matches.forEach(match -> {
            RecurringExpenseTransactionCandidate transaction = transactions.get(match.getTransactionId());
            if (transaction != null) {
                result.put(new OccurrenceIdentity(match.getRecurringExpenseId(), match.getDueDate()),
                        new MatchDetails(match, transaction));
            }
        });
        return Map.copyOf(result);
    }

    private RecurringExpenseMatch match(UUID ownerId, UUID recurringExpenseId, LocalDate dueDate,
                                        RecurringExpenseTransactionCandidate transaction, boolean replace) {
        RecurringExpense expense = findOwned(ownerId, recurringExpenseId);
        if (expense.getStatus() != RecurringExpenseStatus.ACTIVE) {
            throw new RecurringExpenseConflictException("An archived recurring expense cannot be matched");
        }
        ensureOccurrence(expense, dueDate);
        validateCandidate(expense, transaction, true);

        Optional<RecurringExpenseMatch> occurrenceMatch = matchRepository
                .findByOwnerIdAndRecurringExpenseIdAndDueDate(ownerId, recurringExpenseId, dueDate);
        Optional<RecurringExpenseMatch> transactionMatch = matchRepository
                .findByOwnerIdAndTransactionId(ownerId, transaction.id());
        boolean sameMatch = occurrenceMatch.isPresent() && transactionMatch.isPresent()
                && occurrenceMatch.get().getId().equals(transactionMatch.get().getId());
        if (!replace && (occurrenceMatch.isPresent() || transactionMatch.isPresent())) {
            throw new RecurringExpenseConflictException(
                    sameMatch ? "The occurrence is already matched to this transaction"
                            : "The occurrence or transaction is already matched"
            );
        }
        if (sameMatch) {
            return occurrenceMatch.get();
        }
        transactionMatch.filter(match -> occurrenceMatch.isEmpty()
                        || !match.getId().equals(occurrenceMatch.get().getId()))
                .ifPresent(matchRepository::delete);

        RecurringExpenseMatch result = occurrenceMatch.orElseGet(() -> new RecurringExpenseMatch(
                UUID.randomUUID(), ownerId, recurringExpenseId, dueDate, transaction.id()));
        result.replaceTransaction(transaction.id());
        try {
            return matchRepository.saveAndFlush(result);
        } catch (DataIntegrityViolationException exception) {
            throw new RecurringExpenseConflictException("The occurrence or transaction is already matched");
        }
    }

    private void validateCandidate(RecurringExpense expense,
                                   RecurringExpenseTransactionCandidate transaction,
                                   boolean requireActive) {
        if (!expense.getOwnerId().equals(transaction.ownerId())) {
            throw new RecurringExpenseConflictException("Transaction and recurring expense owners must match");
        }
        if (requireActive && !transaction.active()) {
            throw new RecurringExpenseConflictException("A deleted transaction cannot satisfy an occurrence");
        }
        if (!transaction.expense() || transaction.amount().signum() <= 0) {
            throw new RecurringExpenseConflictException(
                    "Only a positive expense transaction can satisfy an occurrence"
            );
        }
        if (transaction.split()) {
            throw new RecurringExpenseConflictException(
                    "A split transaction cannot satisfy a recurring expense occurrence"
            );
        }
        if (!Objects.equals(expense.getCategoryId(), transaction.categoryId())) {
            throw new RecurringExpenseConflictException(
                    "Transaction and recurring expense categories must match"
            );
        }
        if (expense.getAccountId() != null
                && !expense.getAccountId().equals(transaction.accountId())) {
            throw new RecurringExpenseConflictException(
                    "Transaction and recurring expense accounts must match"
            );
        }
        if (!expense.getCurrency().equalsIgnoreCase(transaction.currency())) {
            throw new RecurringExpenseConflictException(
                    "Transaction and recurring expense currencies must match"
            );
        }
    }

    private RecurringExpenseOccurrenceResponse occurrence(UUID ownerId, UUID recurringExpenseId,
                                                          LocalDate dueDate) {
        RecurringExpense expense = findOwned(ownerId, recurringExpenseId);
        ensureOccurrence(expense, dueDate);
        MatchDetails details = matchRepository
                .findByOwnerIdAndRecurringExpenseIdAndDueDate(ownerId, recurringExpenseId, dueDate)
                .map(match -> new MatchDetails(match,
                        transactionSource.findOwned(ownerId, match.getTransactionId())))
                .orElse(null);
        return response(expense, dueDate, details);
    }

    RecurringExpenseOccurrenceResponse response(RecurringExpense expense, LocalDate dueDate,
                                                MatchDetails details) {
        RecurringExpenseTransactionCandidate transaction = details == null ? null : details.transaction();
        boolean satisfied = transaction != null && transaction.active();
        BigDecimal actual = satisfied ? transaction.amount() : null;
        return new RecurringExpenseOccurrenceResponse(
                expense.getId() + ":" + dueDate, expense.getId(), expense.getName(), dueDate,
                expense.getAmount(), expense.getAmount(), actual,
                actual == null ? null : expense.getAmount().subtract(actual),
                satisfied ? RecurringExpenseOccurrenceStatus.SATISFIED
                        : RecurringExpenseOccurrenceStatus.OUTSTANDING,
                transaction == null ? null : RecurringExpenseLinkedTransactionResponse.from(transaction),
                expense.getCurrency(), expense.getCategoryId(), expense.getAccountId()
        );
    }

    private RecurringExpense findOwned(UUID ownerId, UUID recurringExpenseId) {
        return expenseRepository.findByIdAndOwnerId(recurringExpenseId, ownerId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(recurringExpenseId));
    }

    private void ensureOccurrence(RecurringExpense expense, LocalDate dueDate) {
        if (dueDate == null || projector.dueDates(expense, dueDate, dueDate).isEmpty()) {
            throw new RecurringExpenseConflictException(
                    "The due date does not identify an occurrence in the recurring expense schedule"
            );
        }
    }

    record OccurrenceIdentity(UUID recurringExpenseId, LocalDate dueDate) { }

    record MatchDetails(RecurringExpenseMatch match,
                        RecurringExpenseTransactionCandidate transaction) { }
}
