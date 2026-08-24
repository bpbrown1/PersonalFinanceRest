package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.account.management.AccountStatus;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.shared.money.MoneyValues;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class TransferService {

    private final FinancialTransactionRepository repository;
    private final FinancialAccountRepository accountRepository;
    private final CurrentUserProvider currentUserProvider;

    TransferService(FinancialTransactionRepository repository,
                    FinancialAccountRepository accountRepository,
                    CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    TransferResponse create(CreateTransferRequest request) {
        UUID ownerId = currentUserProvider.userId();
        validateDistinctAccounts(request.sourceAccountId(), request.destinationAccountId());
        Map<UUID, FinancialAccount> accounts = lockOwnedAccounts(
                ownerId, List.of(request.sourceAccountId(), request.destinationAccountId())
        );
        FinancialAccount sourceAccount = accounts.get(request.sourceAccountId());
        FinancialAccount destinationAccount = accounts.get(request.destinationAccountId());
        validateAccounts(sourceAccount, destinationAccount, request.transactionDate());
        BigDecimal sourceAmount = MoneyValues.amountOrZero(request.sourceAmount());
        BigDecimal destinationAmount = MoneyValues.amountOrZero(request.destinationAmount());
        validateCurrencyAmounts(sourceAccount, destinationAccount, sourceAmount, destinationAmount);

        UUID transferId = UUID.randomUUID();
        List<FinancialTransaction> legs = List.of(
                FinancialTransaction.transferLeg(
                        UUID.randomUUID(), transferId, ownerId, sourceAccount.getId(), sourceAmount,
                        TransactionType.TRANSFER_OUT, request.transactionDate(), request.description(),
                        request.notes(), request.externalReference()
                ),
                FinancialTransaction.transferLeg(
                        UUID.randomUUID(), transferId, ownerId, destinationAccount.getId(), destinationAmount,
                        TransactionType.TRANSFER_IN, request.transactionDate(), request.description(),
                        request.notes(), request.externalReference()
                )
        );
        List<FinancialTransaction> saved = repository.saveAllAndFlush(legs);
        sourceAccount.applyBalanceDelta(sourceAmount.negate());
        destinationAccount.applyBalanceDelta(destinationAmount);
        accountRepository.saveAllAndFlush(accounts.values());
        return TransferResponse.from(saved);
    }

    @Transactional(readOnly = true)
    List<TransferResponse> findAll(TransactionStatusFilter status) {
        Boolean deleted = switch (status) {
            case ACTIVE -> false;
            case DELETED -> true;
            case ALL -> null;
        };
        Map<UUID, List<FinancialTransaction>> byTransfer = new LinkedHashMap<>();
        repository.findTransferLegs(currentUserProvider.userId(), deleted).forEach(leg ->
                byTransfer.computeIfAbsent(leg.getTransferId(), ignored -> new ArrayList<>()).add(leg));
        return byTransfer.values().stream().map(TransferResponse::from).toList();
    }

    @Transactional(readOnly = true)
    TransferResponse findById(UUID transferId) {
        return TransferResponse.from(findOwnedTransfer(transferId));
    }

    @Transactional
    TransferResponse update(UUID transferId, UpdateTransferRequest request) {
        UUID ownerId = currentUserProvider.userId();
        List<FinancialTransaction> legs = findOwnedTransfer(transferId);
        FinancialTransaction source = leg(legs, TransactionType.TRANSFER_OUT);
        FinancialTransaction destination = leg(legs, TransactionType.TRANSFER_IN);
        validateDistinctAccounts(request.sourceAccountId(), request.destinationAccountId());
        Map<UUID, FinancialAccount> accounts = lockOwnedAccounts(ownerId, List.of(
                source.getAccountId(), destination.getAccountId(),
                request.sourceAccountId(), request.destinationAccountId()
        ));
        FinancialAccount newSource = accounts.get(request.sourceAccountId());
        FinancialAccount newDestination = accounts.get(request.destinationAccountId());
        validateAccounts(newSource, newDestination, request.transactionDate());
        BigDecimal sourceAmount = MoneyValues.amountOrZero(request.sourceAmount());
        BigDecimal destinationAmount = MoneyValues.amountOrZero(request.destinationAmount());
        validateCurrencyAmounts(newSource, newDestination, sourceAmount, destinationAmount);

        if (source.getStatus() == TransactionStatus.ACTIVE) {
            accounts.get(source.getAccountId()).applyBalanceDelta(source.balanceImpact().negate());
            accounts.get(destination.getAccountId()).applyBalanceDelta(destination.balanceImpact().negate());
        }
        source.replaceTransferLeg(
                request.sourceAccountId(), sourceAmount, TransactionType.TRANSFER_OUT,
                request.transactionDate(), request.description(), request.notes(), request.externalReference()
        );
        destination.replaceTransferLeg(
                request.destinationAccountId(), destinationAmount, TransactionType.TRANSFER_IN,
                request.transactionDate(), request.description(), request.notes(), request.externalReference()
        );
        if (source.getStatus() == TransactionStatus.ACTIVE) {
            newSource.applyBalanceDelta(source.balanceImpact());
            newDestination.applyBalanceDelta(destination.balanceImpact());
        }
        List<FinancialTransaction> saved = repository.saveAllAndFlush(legs);
        accountRepository.saveAllAndFlush(accounts.values());
        return TransferResponse.from(saved);
    }

    @Transactional
    TransferResponse delete(UUID transferId) {
        UUID ownerId = currentUserProvider.userId();
        List<FinancialTransaction> legs = findOwnedTransfer(transferId);
        if (legs.getFirst().getStatus() == TransactionStatus.ACTIVE) {
            Map<UUID, FinancialAccount> accounts = lockOwnedAccounts(
                    ownerId, legs.stream().map(FinancialTransaction::getAccountId).toList()
            );
            Instant deletedAt = Instant.now();
            legs.forEach(leg -> {
                accounts.get(leg.getAccountId()).applyBalanceDelta(leg.balanceImpact().negate());
                leg.softDelete(deletedAt);
            });
            accountRepository.saveAllAndFlush(accounts.values());
        }
        return TransferResponse.from(repository.saveAllAndFlush(legs));
    }

    @Transactional
    TransferResponse restore(UUID transferId) {
        UUID ownerId = currentUserProvider.userId();
        List<FinancialTransaction> legs = findOwnedTransfer(transferId);
        if (legs.getFirst().getStatus() == TransactionStatus.DELETED) {
            Map<UUID, FinancialAccount> accounts = lockOwnedAccounts(
                    ownerId, legs.stream().map(FinancialTransaction::getAccountId).toList()
            );
            FinancialTransaction source = leg(legs, TransactionType.TRANSFER_OUT);
            FinancialTransaction destination = leg(legs, TransactionType.TRANSFER_IN);
            validateAccounts(
                    accounts.get(source.getAccountId()), accounts.get(destination.getAccountId()),
                    source.getTransactionDate()
            );
            validateCurrencyAmounts(
                    accounts.get(source.getAccountId()), accounts.get(destination.getAccountId()),
                    source.getAmount(), destination.getAmount()
            );
            legs.forEach(leg -> {
                accounts.get(leg.getAccountId()).applyBalanceDelta(leg.balanceImpact());
                leg.restore();
            });
            accountRepository.saveAllAndFlush(accounts.values());
        }
        return TransferResponse.from(repository.saveAllAndFlush(legs));
    }

    private List<FinancialTransaction> findOwnedTransfer(UUID transferId) {
        List<FinancialTransaction> legs = repository
                .findAllByTransferIdAndOwnerIdOrderByType(transferId, currentUserProvider.userId());
        if (legs.isEmpty()) {
            throw new TransferNotFoundException(transferId);
        }
        TransferResponse.from(legs);
        return legs;
    }

    private FinancialTransaction leg(List<FinancialTransaction> legs, TransactionType type) {
        return legs.stream().filter(candidate -> candidate.getType() == type).findFirst()
                .orElseThrow(() -> new TransactionConflictException("A linked transfer leg is missing: " + type));
    }

    private Map<UUID, FinancialAccount> lockOwnedAccounts(UUID ownerId, Collection<UUID> accountIds) {
        Map<UUID, FinancialAccount> accounts = new LinkedHashMap<>();
        accountIds.stream().distinct().sorted(Comparator.comparing(UUID::toString)).forEach(accountId ->
                accounts.put(accountId, accountRepository.findByIdAndOwnerIdForUpdate(accountId, ownerId)
                        .orElseThrow(() -> new FinancialAccountNotFoundException(accountId))));
        return accounts;
    }

    private void validateDistinctAccounts(UUID sourceAccountId, UUID destinationAccountId) {
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new TransactionConflictException("Source and destination accounts must be different");
        }
    }

    private void validateAccounts(FinancialAccount source, FinancialAccount destination, LocalDate date) {
        validateAccount(source, date);
        validateAccount(destination, date);
    }

    private void validateAccount(FinancialAccount account, LocalDate date) {
        if (account.getStatus() == AccountStatus.ARCHIVED) {
            throw new TransactionConflictException(
                    "An archived financial account cannot receive a transfer: " + account.getId()
            );
        }
        if (date.isBefore(account.getOpeningDate())) {
            throw new TransactionConflictException(
                    "Transfer date cannot precede the account opening date: " + account.getOpeningDate()
            );
        }
        if (date.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new TransactionConflictException("Transfer date cannot be in the future: " + date);
        }
    }

    private void validateCurrencyAmounts(FinancialAccount source, FinancialAccount destination,
                                         BigDecimal sourceAmount, BigDecimal destinationAmount) {
        if (source.getCurrency().equals(destination.getCurrency())
                && sourceAmount.compareTo(destinationAmount) != 0) {
            throw new TransactionConflictException(
                    "Same-currency transfers require equal source and destination amounts"
            );
        }
    }
}
