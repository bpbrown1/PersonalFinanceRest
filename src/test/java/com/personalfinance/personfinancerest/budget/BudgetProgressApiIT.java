package com.personalfinance.personfinancerest.budget;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetProgressApiIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private CurrentUserProvider currentUserProvider;

    private UUID ownerId;
    private UUID accountId;
    private UUID euroAccountId;
    private UUID parentId;
    private UUID childId;
    private UUID leafId;
    private UUID otherCategoryId;
    private UUID budgetId;
    private UUID parentLineId;
    private UUID childLineId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM transaction_split");
        jdbcTemplate.update("DELETE FROM financial_transaction");
        jdbcTemplate.update("DELETE FROM budget_line");
        jdbcTemplate.update("DELETE FROM budget");
        jdbcTemplate.update("UPDATE transaction_category SET parent_id = NULL");
        jdbcTemplate.update("DELETE FROM transaction_category");
        jdbcTemplate.update("DELETE FROM account_balance_snapshot");
        jdbcTemplate.update("DELETE FROM financial_account");
        jdbcTemplate.update("DELETE FROM app_user WHERE id <> ?", currentUserProvider.userId());

        ownerId = currentUserProvider.userId();
        accountId = UUID.randomUUID();
        euroAccountId = UUID.randomUUID();
        insertAccount(accountId, ownerId, "USD");
        insertAccount(euroAccountId, ownerId, "EUR");

        parentId = insertCategory(ownerId, "Food", null);
        childId = insertCategory(ownerId, "Dining", parentId);
        leafId = insertCategory(ownerId, "Restaurants", childId);
        otherCategoryId = insertCategory(ownerId, "Household", null);

        Budget budget = new Budget(
                UUID.randomUUID(), ownerId, "August", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );
        parentLineId = budget.addLine(parentId, amount("40.00")).getId();
        childLineId = budget.addLine(childId, amount("60.00")).getId();
        budgetId = budgetRepository.saveAndFlush(budget).getId();
    }

    @Test
    void calculatesBoundariesHierarchyRefundsSplitsExclusionsUnbudgetedAndDrillDown() throws Exception {
        UUID startParent = insertTransaction(ownerId, accountId, parentId, "10.00", "EXPENSE", "2026-08-01", null);
        UUID endChild = insertTransaction(ownerId, accountId, childId, "30.00", "EXPENSE", "2026-08-31", null);
        UUID leafExpense = insertTransaction(ownerId, accountId, leafId, "50.00", "EXPENSE", "2026-08-15", null);
        UUID refund = insertTransaction(ownerId, accountId, leafId, "-5.00", "EXPENSE", "2026-08-16", null);
        UUID household = insertTransaction(ownerId, accountId, otherCategoryId, "20.00", "EXPENSE", "2026-08-17", null);
        UUID uncategorized = insertTransaction(ownerId, accountId, null, "4.00", "EXPENSE", "2026-08-18", null);
        UUID split = insertTransaction(ownerId, accountId, null, "15.00", "EXPENSE", "2026-08-19", null);
        insertSplit(split, childId, "5.00", 0);
        insertSplit(split, otherCategoryId, "10.00", 1);

        insertTransaction(ownerId, accountId, parentId, "100.00", "INCOME", "2026-08-10", null);
        insertTransaction(ownerId, accountId, parentId, "100.00", "EXPENSE", "2026-08-10", "CURRENT_TIMESTAMP");
        insertTransaction(ownerId, accountId, parentId, "100.00", "EXPENSE", "2026-07-31", null);
        insertTransaction(ownerId, accountId, parentId, "100.00", "EXPENSE", "2026-09-01", null);
        insertTransaction(ownerId, euroAccountId, parentId, "100.00", "EXPENSE", "2026-08-10", null);
        insertTransfer(ownerId, accountId, "100.00", "2026-08-10");

        UUID otherOwner = UUID.randomUUID();
        UUID otherAccount = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, display_name) VALUES (?, 'Other')", otherOwner);
        insertAccount(otherAccount, otherOwner, "USD");
        UUID otherOwnerCategory = insertCategory(otherOwner, "Private", null);
        insertTransaction(otherOwner, otherAccount, otherOwnerCategory, "100.00", "EXPENSE", "2026-08-10", null);

        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", budgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId").value(budgetId.toString()))
                .andExpect(jsonPath("$.planned").value(100.0))
                .andExpect(jsonPath("$.budgetedActual").value(90.0))
                .andExpect(jsonPath("$.unbudgetedActual").value(34.0))
                .andExpect(jsonPath("$.totalActual").value(124.0))
                .andExpect(jsonPath("$.remaining").value(-24.0))
                .andExpect(jsonPath("$.percentageUsed").value(124.0))
                .andExpect(jsonPath("$.lines[0].lineId").value(parentLineId.toString()))
                .andExpect(jsonPath("$.lines[0].actual").value(10.0))
                .andExpect(jsonPath("$.lines[1].lineId").value(childLineId.toString()))
                .andExpect(jsonPath("$.lines[1].actual").value(80.0))
                .andExpect(jsonPath("$.lines[1].remaining").value(-20.0))
                .andExpect(jsonPath("$.lines[1].percentageUsed").value(133.33))
                .andExpect(jsonPath("$.lines[1].drillDown.transactionIds.length()").value(4))
                .andExpect(jsonPath("$.unbudgeted.length()").value(2))
                .andExpect(jsonPath("$.drillDown.transactionIds.length()").value(7))
                .andExpect(jsonPath("$.drillDown.transactionIds").value(org.hamcrest.Matchers.hasItems(
                        startParent.toString(), endChild.toString(), leafExpense.toString(), refund.toString(),
                        household.toString(), uncategorized.toString(), split.toString()
                )));
    }

    @Test
    void appliesCategoryAndAccountFiltersAndProtectsOwnership() throws Exception {
        insertTransaction(ownerId, accountId, parentId, "10.00", "EXPENSE", "2026-08-05", null);
        insertTransaction(ownerId, accountId, leafId, "25.00", "EXPENSE", "2026-08-06", null);

        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", budgetId)
                        .queryParam("accountId", accountId.toString())
                        .queryParam("categoryId", childId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.categoryId").value(childId.toString()))
                .andExpect(jsonPath("$.budgetedActual").value(25.0))
                .andExpect(jsonPath("$.unbudgetedActual").value(0.0))
                .andExpect(jsonPath("$.lines[0].actual").value(0.0))
                .andExpect(jsonPath("$.lines[1].actual").value(25.0));

        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", budgetId)
                        .queryParam("accountId", euroAccountId.toString()))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", budgetId)
                        .queryParam("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        UUID otherOwner = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, display_name) VALUES (?, 'Other')", otherOwner);
        Budget foreign = budgetRepository.saveAndFlush(new Budget(
                UUID.randomUUID(), otherOwner, "Private", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        ));
        mockMvc.perform(get("/api/v1/budgets/{budgetId}/progress", foreign.getId()))
                .andExpect(status().isNotFound());
    }

    private void insertAccount(UUID id, UUID accountOwnerId, String currency) {
        jdbcTemplate.update("""
                INSERT INTO financial_account
                (id, owner_id, name, type, currency, opening_date, opening_balance, current_balance,
                 created_at, updated_at, archived_at)
                VALUES (?, ?, 'Test', 'CHECKING', ?, DATE '2026-01-01', 0, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """, id, accountOwnerId, currency);
    }

    private UUID insertCategory(UUID categoryOwnerId, String name, UUID parentId) {
        UUID id = UUID.randomUUID();
        String normalized = name.toLowerCase();
        jdbcTemplate.update("""
                INSERT INTO transaction_category
                (id, owner_id, name, normalized_name, active_name_key, applicability,
                 archived_at, parent_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'EXPENSE', NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, categoryOwnerId, name, normalized, normalized, parentId);
        return id;
    }

    private UUID insertTransaction(UUID transactionOwnerId, UUID transactionAccountId, UUID categoryId,
                                   String amount, String type, String date, String deletedAtExpression) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO financial_transaction
                (id, owner_id, account_id, category_id, transfer_id, amount, type, transaction_date,
                 description, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?, ?, 'Test', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, transactionOwnerId, transactionAccountId, categoryId,
                new BigDecimal(amount), type, LocalDate.parse(date),
                deletedAtExpression == null ? null : java.time.Instant.now());
        return id;
    }

    private void insertSplit(UUID transactionId, UUID categoryId, String amount, int position) {
        jdbcTemplate.update("""
                INSERT INTO transaction_split (id, transaction_id, category_id, amount, position)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), transactionId, categoryId, new BigDecimal(amount), position);
    }

    private void insertTransfer(UUID transferOwnerId, UUID transferAccountId, String amount, String date) {
        jdbcTemplate.update("""
                INSERT INTO financial_transaction
                (id, owner_id, account_id, category_id, transfer_id, amount, type, transaction_date,
                 description, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, NULL, ?, ?, 'TRANSFER_OUT', ?, 'Transfer', NULL,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), transferOwnerId, transferAccountId, UUID.randomUUID(),
                new BigDecimal(amount), LocalDate.parse(date));
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
