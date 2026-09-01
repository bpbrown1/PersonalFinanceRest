package com.personalfinance.personfinancerest.web;

import com.personalfinance.personfinancerest.account.balance.AccountBalanceNotFoundException;
import com.personalfinance.personfinancerest.account.balance.ArchivedFinancialAccountException;
import com.personalfinance.personfinancerest.account.balance.BalanceSnapshotConflictException;
import com.personalfinance.personfinancerest.account.balance.BalanceSnapshotNotFoundException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountInUseException;
import com.personalfinance.personfinancerest.account.management.FinancialAccountNotFoundException;
import com.personalfinance.personfinancerest.account.management.InvalidAccountStatusException;
import com.personalfinance.personfinancerest.category.CategoryNotFoundException;
import com.personalfinance.personfinancerest.category.CategoryHierarchyConflictException;
import com.personalfinance.personfinancerest.category.DuplicateCategoryNameException;
import com.personalfinance.personfinancerest.category.InvalidCategoryStatusException;
import com.personalfinance.personfinancerest.budget.BudgetConflictException;
import com.personalfinance.personfinancerest.budget.BudgetTargetMonthConflictException;
import com.personalfinance.personfinancerest.budget.BudgetLineNotFoundException;
import com.personalfinance.personfinancerest.budget.BudgetNotFoundException;
import com.personalfinance.personfinancerest.budget.InvalidBudgetRequestException;
import com.personalfinance.personfinancerest.budget.InvalidBudgetStatusException;
import com.personalfinance.personfinancerest.transaction.InvalidTransactionDateRangeException;
import com.personalfinance.personfinancerest.transaction.InvalidTransactionStatusException;
import com.personalfinance.personfinancerest.transaction.InvalidTransactionSearchException;
import com.personalfinance.personfinancerest.transaction.InvalidTransactionAllocationException;
import com.personalfinance.personfinancerest.transaction.TransactionConflictException;
import com.personalfinance.personfinancerest.transaction.TransactionNotFoundException;
import com.personalfinance.personfinancerest.transaction.TransferNotFoundException;
import com.personalfinance.personfinancerest.recurringexpense.InvalidRecurringExpenseRequestException;
import com.personalfinance.personfinancerest.recurringexpense.InvalidRecurringExpenseStatusException;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseConflictException;
import com.personalfinance.personfinancerest.recurringexpense.RecurringExpenseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({BalanceSnapshotConflictException.class, ArchivedFinancialAccountException.class})
    ResponseEntity<ApiError> handleBalanceConflict(RuntimeException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler({AccountBalanceNotFoundException.class, BalanceSnapshotNotFoundException.class})
    ResponseEntity<ApiError> handleBalanceNotFound(RuntimeException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FinancialAccountInUseException.class)
    ResponseEntity<ApiError> handleFinancialAccountInUse(FinancialAccountInUseException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DuplicateCategoryNameException.class)
    ResponseEntity<ApiError> handleDuplicateCategoryName(DuplicateCategoryNameException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(CategoryHierarchyConflictException.class)
    ResponseEntity<ApiError> handleCategoryHierarchyConflict(CategoryHierarchyConflictException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(TransactionConflictException.class)
    ResponseEntity<ApiError> handleTransactionConflict(TransactionConflictException exception) {
        ApiError response = new ApiError(
                Instant.now(), HttpStatus.CONFLICT.value(), exception.getMessage(), Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BudgetConflictException.class)
    ResponseEntity<ApiError> handleBudgetConflict(BudgetConflictException exception) {
        ApiError response = new ApiError(
                Instant.now(), HttpStatus.CONFLICT.value(), exception.getMessage(), Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RecurringExpenseConflictException.class)
    ResponseEntity<ApiError> handleRecurringExpenseConflict(RecurringExpenseConflictException exception) {
        ApiError response = new ApiError(
                Instant.now(), HttpStatus.CONFLICT.value(), exception.getMessage(), Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BudgetTargetMonthConflictException.class)
    ResponseEntity<ApiError> handleBudgetTargetMonthConflict(BudgetTargetMonthConflictException exception) {
        ApiError response = new ApiError(Instant.now(), HttpStatus.CONFLICT.value(),
                exception.getMessage(), Map.of(), exception.getExistingBudgetId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(InvalidAccountStatusException.class)
    ResponseEntity<ApiError> handleInvalidAccountStatus(InvalidAccountStatusException exception) {
        return badRequest("Validation failed", Map.of("status", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCategoryStatusException.class)
    ResponseEntity<ApiError> handleInvalidCategoryStatus(InvalidCategoryStatusException exception) {
        return badRequest("Validation failed", Map.of("status", exception.getMessage()));
    }

    @ExceptionHandler(InvalidTransactionStatusException.class)
    ResponseEntity<ApiError> handleInvalidTransactionStatus(InvalidTransactionStatusException exception) {
        return badRequest("Validation failed", Map.of("status", exception.getMessage()));
    }

    @ExceptionHandler(InvalidBudgetStatusException.class)
    ResponseEntity<ApiError> handleInvalidBudgetStatus(InvalidBudgetStatusException exception) {
        return badRequest("Validation failed", Map.of("status", exception.getMessage()));
    }

    @ExceptionHandler(InvalidBudgetRequestException.class)
    ResponseEntity<ApiError> handleInvalidBudgetRequest(InvalidBudgetRequestException exception) {
        return badRequest("Validation failed", exception.getFieldErrors());
    }

    @ExceptionHandler(InvalidRecurringExpenseStatusException.class)
    ResponseEntity<ApiError> handleInvalidRecurringExpenseStatus(InvalidRecurringExpenseStatusException exception) {
        return badRequest("Validation failed", Map.of("status", exception.getMessage()));
    }

    @ExceptionHandler(InvalidRecurringExpenseRequestException.class)
    ResponseEntity<ApiError> handleInvalidRecurringExpenseRequest(InvalidRecurringExpenseRequestException exception) {
        return badRequest("Validation failed", exception.getFieldErrors());
    }

    @ExceptionHandler(InvalidTransactionDateRangeException.class)
    ResponseEntity<ApiError> handleInvalidTransactionDateRange(InvalidTransactionDateRangeException exception) {
        return badRequest("Validation failed", Map.of("dateRange", exception.getMessage()));
    }

    @ExceptionHandler(InvalidTransactionSearchException.class)
    ResponseEntity<ApiError> handleInvalidTransactionSearch(InvalidTransactionSearchException exception) {
        return badRequest("Validation failed", Map.of(exception.getField(), exception.getMessage()));
    }

    @ExceptionHandler(InvalidTransactionAllocationException.class)
    ResponseEntity<ApiError> handleInvalidTransactionAllocation(InvalidTransactionAllocationException exception) {
        return badRequest("Validation failed", exception.getFieldErrors());
    }

    @ExceptionHandler(FinancialAccountNotFoundException.class)
    ResponseEntity<ApiError> handleFinancialAccountNotFound(FinancialAccountNotFoundException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ApiError> handleCategoryNotFound(CategoryNotFoundException exception) {
        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({TransactionNotFoundException.class, TransferNotFoundException.class})
    ResponseEntity<ApiError> handleTransactionNotFound(RuntimeException exception) {
        ApiError response = new ApiError(
                Instant.now(), HttpStatus.NOT_FOUND.value(), exception.getMessage(), Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({BudgetNotFoundException.class, BudgetLineNotFoundException.class})
    ResponseEntity<ApiError> handleBudgetNotFound(RuntimeException exception) {
        ApiError response = new ApiError(
                Instant.now(), HttpStatus.NOT_FOUND.value(), exception.getMessage(), Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(RecurringExpenseNotFoundException.class)
    ResponseEntity<ApiError> handleRecurringExpenseNotFound(RecurringExpenseNotFoundException exception) {
        ApiError response = new ApiError(
                Instant.now(), HttpStatus.NOT_FOUND.value(), exception.getMessage(), Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return badRequest("Validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableMessage() {
        return badRequest("Request body is malformed", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("Validation failed", Map.of(
                exception.getName(), "has an invalid value"
        ));
    }

    private ResponseEntity<ApiError> badRequest(String error, Map<String, String> fieldErrors) {
        ApiError response = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), error, fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }
}
