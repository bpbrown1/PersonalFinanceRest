package com.personalfinance.personfinancerest.recurringexpense;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringExpenseApiIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CurrentUserProvider currentUserProvider;

    private UUID ownerId;
    private UUID accountId;
    private UUID euroAccountId;
    private UUID categoryId;
    private UUID childCategoryId;
    private UUID otherCategoryId;
    private UUID incomeCategoryId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM recurring_expense_match");
        jdbc.update("DELETE FROM recurring_expense");
        jdbc.update("DELETE FROM transaction_split");
        jdbc.update("DELETE FROM financial_transaction");
        jdbc.update("DELETE FROM budget_line");
        jdbc.update("DELETE FROM budget");
        jdbc.update("UPDATE transaction_category SET parent_id = NULL");
        jdbc.update("DELETE FROM transaction_category");
        jdbc.update("DELETE FROM account_balance_snapshot");
        jdbc.update("DELETE FROM financial_account");
        jdbc.update("DELETE FROM app_user WHERE id <> ?", currentUserProvider.userId());

        ownerId = currentUserProvider.userId();
        accountId = insertAccount(ownerId, "USD");
        euroAccountId = insertAccount(ownerId, "EUR");
        categoryId = insertCategory(ownerId, "Housing", "EXPENSE", null);
        childCategoryId = insertCategory(ownerId, "Utilities", "EXPENSE", categoryId);
        otherCategoryId = insertCategory(ownerId, "Insurance", "EXPENSE", null);
        incomeCategoryId = insertCategory(ownerId, "Salary", "INCOME", null);
    }

    @Test
    void createsListsUpdatesArchivesRestoresAndProjectsOccurrences() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Internet", "89.90", "usd", childCategoryId, accountId,
                                "2026-01-31", null, 1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "/api/v1/recurring-expenses/")))
                .andExpect(jsonPath("$.name").value("Internet"))
                .andExpect(jsonPath("$.amount").value(89.9))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("active"));

        UUID id = jdbc.queryForObject("SELECT id FROM recurring_expense WHERE name = 'Internet'", UUID.class);
        mockMvc.perform(get("/api/v1/recurring-expenses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(get("/api/v1/recurring-expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));

        mockMvc.perform(get("/api/v1/recurring-expenses/occurrences")
                        .queryParam("from", "2026-01-01").queryParam("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].dueDate", containsInAnyOrder(
                        "2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30")))
                .andExpect(jsonPath("$[0].occurrenceKey").value(id + ":2026-01-31"));

        mockMvc.perform(put("/api/v1/recurring-expenses/{id}", id)
                        .contentType("application/json")
                        .content(request("Internet and TV", "99.95", "USD", childCategoryId, accountId,
                                "2026-01-31", "2027-01-31", 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Internet and TV"))
                .andExpect(jsonPath("$.intervalMonths").value(6));

        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/archive", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("archived"));
        mockMvc.perform(get("/api/v1/recurring-expenses"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/recurring-expenses").queryParam("status", "archived"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
        mockMvc.perform(put("/api/v1/recurring-expenses/{id}", id)
                        .contentType("application/json")
                        .content(request("Blocked", "1.00", "USD", childCategoryId, accountId,
                                "2026-01-31", null, 1)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/restore", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void validatesDatesCurrencyAssociationsRangesAndOwnership() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Bad dates", "10.00", "USD", categoryId, accountId,
                                "2026-06-01", "2026-05-31", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Income category", "10.00", "USD", incomeCategoryId, accountId,
                                "2026-06-01", null, 1)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Currency mismatch", "10.00", "USD", categoryId, euroAccountId,
                                "2026-06-01", null, 1)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Obsolete", "10.00", "BGN", categoryId, null,
                                "2026-06-01", null, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());
        mockMvc.perform(get("/api/v1/recurring-expenses/occurrences")
                        .queryParam("from", "2026-08-02").queryParam("to", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateRange").exists());
        mockMvc.perform(get("/api/v1/recurring-expenses").queryParam("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        UUID otherOwner = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, display_name) VALUES (?, 'Other')", otherOwner);
        UUID foreignCategory = insertCategory(otherOwner, "Private", "EXPENSE", null);
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Foreign", "10.00", "USD", foreignCategory, null,
                                "2026-06-01", null, 1)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/recurring-expenses/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void retainedArchivedAssociationsRemainEditableButMustBeActiveForRestore() throws Exception {
        mockMvc.perform(post("/api/v1/recurring-expenses")
                        .contentType("application/json")
                        .content(request("Retained", "10.00", "USD", categoryId, accountId,
                                "2026-06-01", null, 1)))
                .andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("SELECT id FROM recurring_expense WHERE name = 'Retained'", UUID.class);
        jdbc.update("UPDATE transaction_category SET archived_at = CURRENT_TIMESTAMP, active_name_key = NULL "
                + "WHERE id = ?", categoryId);
        jdbc.update("UPDATE financial_account SET archived_at = CURRENT_TIMESTAMP WHERE id = ?", accountId);

        mockMvc.perform(put("/api/v1/recurring-expenses/{id}", id)
                        .contentType("application/json")
                        .content(request("Retained edited", "11.00", "USD", categoryId, accountId,
                                "2026-06-01", null, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Retained edited"));
        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/archive", id))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/restore", id))
                .andExpect(status().isConflict());
    }

    @Test
    void explicitlyLinksReplacesAndUnlinksAnOccurrence() throws Exception {
        UUID recurringId = insertRecurring(
                "Water", "80.00", childCategoryId, accountId, "2026-01-15", 1);
        UUID firstTransaction = insertTransaction(
                ownerId, accountId, childCategoryId, "72.00", "EXPENSE", "2026-08-14", null);
        UUID replacementTransaction = insertTransaction(
                ownerId, accountId, childCategoryId, "78.50", "EXPENSE", "2026-08-16", null);

        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                        recurringId, "2026-08-15")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"" + firstTransaction + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("satisfied"))
                .andExpect(jsonPath("$.targetAmount").value(80.0))
                .andExpect(jsonPath("$.actualAmount").value(72.0))
                .andExpect(jsonPath("$.variance").value(8.0))
                .andExpect(jsonPath("$.linkedTransaction.id").value(firstTransaction.toString()));

        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                        recurringId, "2026-08-15")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"" + firstTransaction + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                        recurringId, "2026-08-15")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"" + replacementTransaction + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualAmount").value(78.5))
                .andExpect(jsonPath("$.variance").value(1.5))
                .andExpect(jsonPath("$.linkedTransaction.id").value(replacementTransaction.toString()));
        mockMvc.perform(get("/api/v1/transactions/{id}", firstTransaction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringExpenseOccurrence").doesNotExist());
        mockMvc.perform(get("/api/v1/transactions/{id}", replacementTransaction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringExpenseOccurrence.occurrenceKey")
                        .value(recurringId + ":2026-08-15"));

        mockMvc.perform(delete("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                        recurringId, "2026-08-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("outstanding"))
                .andExpect(jsonPath("$.actualAmount").doesNotExist())
                .andExpect(jsonPath("$.linkedTransaction").doesNotExist());

        String updateWithOccurrence = """
                {"accountId":"%s","amount":78.50,"transactionDate":"2026-08-16",
                 "description":"Updated recurring payment","type":"EXPENSE","categoryId":"%s",
                 "recurringExpenseOccurrence":{"recurringExpenseId":"%s","dueDate":"2026-08-15"}}
                """.formatted(accountId, childCategoryId, recurringId);
        mockMvc.perform(put("/api/v1/transactions/{id}", replacementTransaction)
                        .contentType("application/json").content(updateWithOccurrence))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringExpenseOccurrence.occurrenceKey")
                        .value(recurringId + ":2026-08-15"));
        mockMvc.perform(put("/api/v1/transactions/{id}", replacementTransaction)
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","amount":78.50,"transactionDate":"2026-08-16",
                                 "description":"Unlinked recurring payment","type":"EXPENSE","categoryId":"%s"}
                                """.formatted(accountId, childCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringExpenseOccurrence").doesNotExist());
    }

    @Test
    void transactionSelectionRetainsDeletedMatchAndRevalidatesRestore() throws Exception {
        UUID recurringId = insertRecurring(
                "Internet", "89.99", childCategoryId, accountId, "2026-01-31", 1);
        String transactionRequest = """
                {"accountId":"%s","amount":84.99,"transactionDate":"2026-08-31",
                 "description":"August internet","type":"EXPENSE","categoryId":"%s",
                 "recurringExpenseOccurrence":{"recurringExpenseId":"%s","dueDate":"2026-08-31"}}
                """.formatted(accountId, childCategoryId, recurringId);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType("application/json").content(transactionRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurringExpenseOccurrence.occurrenceKey")
                        .value(recurringId + ":2026-08-31"));
        UUID transactionId = jdbc.queryForObject(
                "SELECT id FROM financial_transaction WHERE description = 'August internet'", UUID.class);

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"))
                .andExpect(jsonPath("$.recurringExpenseOccurrence.recurringExpenseId")
                        .value(recurringId.toString()));
        mockMvc.perform(get("/api/v1/recurring-expenses/occurrences")
                        .queryParam("from", "2026-08-31").queryParam("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("outstanding"))
                .andExpect(jsonPath("$[0].linkedTransaction.active").value(false));

        mockMvc.perform(post("/api/v1/transactions/{id}/restore", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(put("/api/v1/recurring-expenses/{id}", recurringId)
                        .contentType("application/json")
                        .content(request("Internet", "89.99", "USD", childCategoryId, accountId,
                                "2026-01-30", null, 1)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/archive", recurringId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transactions/{id}/restore", transactionId))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidOccurrenceAndIncompatibleTransactionsWithoutPersistingCreate() throws Exception {
        UUID recurringId = insertRecurring(
                "Water", "80.00", childCategoryId, accountId, "2026-01-15", 1);
        UUID wrongCategoryTransaction = insertTransaction(
                ownerId, accountId, otherCategoryId, "80.00", "EXPENSE", "2026-08-15", null);
        UUID otherUsdAccount = insertAccount(ownerId, "USD");
        UUID wrongAccountTransaction = insertTransaction(
                ownerId, otherUsdAccount, childCategoryId, "80.00", "EXPENSE", "2026-08-15", null);
        UUID incomeTransaction = insertTransaction(
                ownerId, accountId, childCategoryId, "80.00", "INCOME", "2026-08-15", null);
        UUID deletedTransaction = insertTransaction(
                ownerId, accountId, childCategoryId, "80.00", "EXPENSE", "2026-08-15",
                "2026-08-16 00:00:00");

        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                        recurringId, "2026-08-14")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"" + wrongCategoryTransaction + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                        recurringId, "2026-08-15")
                        .contentType("application/json")
                        .content("{\"transactionId\":\"" + wrongCategoryTransaction + "\"}"))
                .andExpect(status().isConflict());
        for (UUID incompatible : java.util.List.of(
                wrongAccountTransaction, incomeTransaction, deletedTransaction)) {
            mockMvc.perform(post("/api/v1/recurring-expenses/{id}/occurrences/{dueDate}/match",
                            recurringId, "2026-08-15")
                            .contentType("application/json")
                            .content("{\"transactionId\":\"" + incompatible + "\"}"))
                    .andExpect(status().isConflict());
        }

        long before = jdbc.queryForObject("SELECT COUNT(*) FROM financial_transaction", Long.class);
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","amount":80.00,"transactionDate":"2026-08-15",
                                 "description":"Wrong category","type":"EXPENSE","categoryId":"%s",
                                 "recurringExpenseOccurrence":{"recurringExpenseId":"%s","dueDate":"2026-08-15"}}
                                """.formatted(accountId, otherCategoryId, recurringId)))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM financial_transaction", Long.class))
                .isEqualTo(before);
    }

    @Test
    void exposesCategoryCommitmentsWithoutChangingBudgetPlansOrActuals() throws Exception {
        UUID budgetId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO budget
                (id, owner_id, name, currency, period_type, start_date, end_date,
                 archived_at, version, created_at, updated_at)
                VALUES (?, ?, 'August', 'USD', 'MONTHLY', DATE '2026-08-01', DATE '2026-08-31',
                        NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, budgetId, ownerId);
        jdbc.update("""
                INSERT INTO budget_line
                (id, budget_id, category_id, planned_amount, position, archived_at, created_at, updated_at)
                VALUES (?, ?, ?, 50.00, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, lineId, budgetId, categoryId);
        UUID power = insertRecurring("Power", "65.00", childCategoryId, accountId, "2026-01-31", 1);
        insertRecurring("Insurance", "20.00", otherCategoryId, null, "2026-02-15", 6);
        insertRecurring("Euro bill", "999.00", otherCategoryId, euroAccountId, "2026-02-15", 6, "EUR");
        UUID powerTransaction = insertTransaction(
                ownerId, accountId, childCategoryId, "60.00", "EXPENSE", "2026-08-31", null);
        jdbc.update("""
                INSERT INTO recurring_expense_match
                (id, owner_id, recurring_expense_id, due_date, transaction_id, created_at, updated_at)
                VALUES (?, ?, ?, DATE '2026-08-31', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), ownerId, power, powerTransaction);

        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", budgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planned").value(50.0))
                .andExpect(jsonPath("$.committed").value(85.0))
                .andExpect(jsonPath("$.remainingAfterCommitments").value(-35.0))
                .andExpect(jsonPath("$.underfunded").value(true))
                .andExpect(jsonPath("$.scheduledTarget").value(85.0))
                .andExpect(jsonPath("$.outstandingScheduledTarget").value(20.0))
                .andExpect(jsonPath("$.totalBudgeted").value(135.0))
                .andExpect(jsonPath("$.totalActual").value(60.0))
                .andExpect(jsonPath("$.remaining").value(75.0))
                .andExpect(jsonPath("$.percentSpent").value(44.44))
                .andExpect(jsonPath("$.projectedUsage").value(80.0))
                .andExpect(jsonPath("$.projectedRemaining").value(55.0))
                .andExpect(jsonPath("$.projectedPercentage").value(59.26))
                .andExpect(jsonPath("$.lines[0].lineId").value(lineId.toString()))
                .andExpect(jsonPath("$.lines[0].planned").value(50.0))
                .andExpect(jsonPath("$.lines[0].committed").value(65.0))
                .andExpect(jsonPath("$.lines[0].outstandingScheduledTarget").value(0.0))
                .andExpect(jsonPath("$.lines[0].totalBudgeted").value(115.0))
                .andExpect(jsonPath("$.lines[0].actual").value(60.0))
                .andExpect(jsonPath("$.lines[0].projectedUsage").value(60.0))
                .andExpect(jsonPath("$.lines[0].scheduledCommitments[0].satisfied").value(true))
                .andExpect(jsonPath("$.lines[0].scheduledCommitments[0].name").value("Power"))
                .andExpect(jsonPath("$.unbudgetedCommitments[0].categoryId")
                        .value(otherCategoryId.toString()))
                .andExpect(jsonPath("$.unbudgetedCommitments[0].committed").value(20.0));

        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", budgetId)
                        .queryParam("accountId", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(65.0))
                .andExpect(jsonPath("$.unbudgetedCommitments.length()").value(0));
    }

    private String request(String name, String amount, String currency, UUID category, UUID account,
                           String anchor, String end, int interval) {
        return """
                {"name":"%s","amount":%s,"currency":"%s","categoryId":"%s",%s
                 "anchorDate":"%s",%s"intervalMonths":%d}
                """.formatted(name, amount, currency, category,
                account == null ? "" : "\"accountId\":\"" + account + "\",",
                anchor, end == null ? "" : "\"endDate\":\"" + end + "\",", interval);
    }

    private UUID insertAccount(UUID accountOwner, String currency) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_account
                (id, owner_id, name, type, currency, opening_date, opening_balance, current_balance,
                 created_at, updated_at, archived_at)
                VALUES (?, ?, 'Account', 'CHECKING', ?, DATE '2026-01-01', 0, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """, id, accountOwner, currency);
        return id;
    }

    private UUID insertCategory(UUID categoryOwner, String name, String applicability, UUID parentId) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbc.update("""
                INSERT INTO transaction_category
                (id, owner_id, name, normalized_name, active_name_key, applicability,
                 archived_at, parent_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, categoryOwner, name, normalized, normalized, applicability, parentId);
        return id;
    }

    private UUID insertRecurring(String name, String amount, UUID category, UUID account,
                                 String anchor, int interval) {
        return insertRecurring(name, amount, category, account, anchor, interval, "USD");
    }

    private UUID insertRecurring(String name, String amount, UUID category, UUID account,
                                 String anchor, int interval, String currency) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recurring_expense
                (id, owner_id, name, amount, currency, category_id, account_id, anchor_date,
                 end_date, interval_months, archived_at, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS DATE), NULL, ?, NULL, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, ownerId, name, new BigDecimal(amount), currency,
                category, account, anchor, interval);
        return id;
    }

    private UUID insertTransaction(UUID transactionOwner, UUID account, UUID category, String amount,
                                   String type, String date, String deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_transaction
                (id, owner_id, account_id, category_id, amount, type, transaction_date, description,
                 merchant_payee, notes, external_reference, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS DATE), 'Recurring payment', NULL, NULL, NULL,
                        CASE WHEN ? IS NULL THEN NULL ELSE CAST(? AS TIMESTAMP) END,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, transactionOwner, account, category, new BigDecimal(amount), type, date,
                deletedAt, deletedAt);
        return id;
    }
}
