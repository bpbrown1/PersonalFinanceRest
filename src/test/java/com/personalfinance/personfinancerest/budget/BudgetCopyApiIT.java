package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetCopyApiIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BudgetRepository repository;
    @Autowired private CurrentUserProvider currentUser;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private BudgetWriteLock writeLock;

    @BeforeEach
    void clearData() {
        jdbc.update("DELETE FROM transaction_split");
        jdbc.update("DELETE FROM financial_transaction");
        jdbc.update("DELETE FROM budget_line");
        jdbc.update("DELETE FROM budget");
        jdbc.update("UPDATE transaction_category SET parent_id = NULL");
        jdbc.update("DELETE FROM transaction_category");
        jdbc.update("DELETE FROM account_balance_snapshot");
        jdbc.update("DELETE FROM financial_account");
        jdbc.update("DELETE FROM app_user WHERE id <> ?", currentUser.userId());
    }

    @Test
    void copiesArchivedSourceWithFreshIdsAndHistoryAndPreservesIndependentEdits() throws Exception {
        UUID rent = category("Rent", "expense");
        UUID old = category("Legacy", "expense");
        UUID utilities = category("Utilities", "both");
        UUID sourceId = create(List.of(line(rent, "1200.25"), line(old, "50.00"), line(utilities, "80.75")));
        JsonNode original = budget(sourceId);
        String rentLine = original.get("lines").get(0).get("id").asText();
        String oldLine = original.get("lines").get(1).get("id").asText();
        String utilityLine = original.get("lines").get(2).get("id").asText();
        mockMvc.perform(put("/api/v1/budgets/{id}/lines/reorder", sourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lineIds", List.of(utilityLine, oldLine, rentLine)))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/lines/{line}/archive", sourceId, oldLine))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/categories/{id}/archive", old)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/budgets/{id}/archive", sourceId)).andExpect(status().isOk());
        jdbc.update("UPDATE budget SET created_at = TIMESTAMP '2020-01-01 00:00:00', version = 7 WHERE id = ?", sourceId);
        jdbc.update("UPDATE budget_line SET created_at = TIMESTAMP '2020-01-01 00:00:00' WHERE budget_id = ?", sourceId);
        JsonNode sourceBefore = budget(sourceId);

        var result = copy(sourceId, "2028-02").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Monthly fixed costs"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.startDate").value("2028-02-01"))
                .andExpect(jsonPath("$.endDate").value("2028-02-29"))
                .andExpect(jsonPath("$.periodType").value("monthly"))
                .andExpect(jsonPath("$.totalPlanned").value(1281.0))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.archivedAt").isEmpty())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].categoryId").value(utilities.toString()))
                .andExpect(jsonPath("$.lines[0].position").value(0))
                .andExpect(jsonPath("$.lines[0].plannedAmount").value(80.75))
                .andExpect(jsonPath("$.lines[1].categoryId").value(rent.toString()))
                .andExpect(jsonPath("$.lines[1].position").value(1))
                .andReturn().getResponse();
        JsonNode copied = mapper.readTree(result.getContentAsString());
        UUID copiedId = UUID.fromString(copied.get("id").asText());
        assertThat(copiedId).isNotEqualTo(sourceId);
        assertThat(result.getHeader("Location")).isEqualTo("/api/v1/budgets/" + copiedId);
        assertThat(copied.get("createdAt")).isNotEqualTo(sourceBefore.get("createdAt"));
        for (JsonNode row : copied.get("lines")) {
            assertThat(row.get("id").asText()).isNotIn(rentLine, oldLine, utilityLine);
            assertThat(row.get("status").asText()).isEqualTo("active");
            assertThat(row.get("archivedAt").isNull()).isTrue();
            assertThat(row.get("createdAt")).isNotEqualTo(sourceBefore.get("lines").get(0).get("createdAt"));
        }
        assertThat(budget(sourceId)).isEqualTo(sourceBefore);

        mockMvc.perform(put("/api/v1/budgets/{id}/lines/{line}", copiedId, copied.get("lines").get(0).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(json(line(utilities, "90.00"))))
                .andExpect(status().isOk());
        assertThat(budget(sourceId)).isEqualTo(sourceBefore);
        JsonNode copyBeforeSourceEdit = budget(copiedId);
        mockMvc.perform(post("/api/v1/budgets/{id}/restore", sourceId)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/budgets/{id}/lines/{line}", sourceId, rentLine)
                        .contentType(MediaType.APPLICATION_JSON).content(json(line(rent, "1400.00"))))
                .andExpect(status().isOk());
        assertThat(budget(copiedId)).isEqualTo(copyBeforeSourceEdit);
        mockMvc.perform(get("/api/v1/budgets/{id}/progress", copiedId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalActual").value(0.0));
    }

    @Test
    void reviewedLinesReplaceTheCompleteTargetSetAndPreserveSubmittedOrder() throws Exception {
        UUID removed = category("Removed", "expense");
        UUID retained = category("Retained", "expense");
        UUID added = category("Added", "both");
        UUID source = create(List.of(line(removed, "10.00"), line(retained, "20.00")));
        JsonNode sourceBefore = budget(source);

        var result = copy(source, Map.of(
                        "targetMonth", "2028-02",
                        "lines", List.of(line(added, "30.50"), line(retained, "25.25"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].categoryId").value(added.toString()))
                .andExpect(jsonPath("$.lines[0].plannedAmount").value(30.5))
                .andExpect(jsonPath("$.lines[0].position").value(0))
                .andExpect(jsonPath("$.lines[1].categoryId").value(retained.toString()))
                .andExpect(jsonPath("$.lines[1].plannedAmount").value(25.25))
                .andExpect(jsonPath("$.lines[1].position").value(1))
                .andExpect(jsonPath("$.totalPlanned").value(55.75))
                .andReturn().getResponse();

        JsonNode copied = mapper.readTree(result.getContentAsString());
        assertThat(copied.get("lines").get(0).get("id").asText())
                .isNotIn(sourceBefore.get("lines").get(0).get("id").asText(),
                        sourceBefore.get("lines").get(1).get("id").asText());
        assertThat(copied.get("lines").get(1).get("id").asText())
                .isNotIn(sourceBefore.get("lines").get(0).get("id").asText(),
                        sourceBefore.get("lines").get(1).get("id").asText());
        assertThat(budget(source)).isEqualTo(sourceBefore);
    }

    @Test
    void explicitEmptyReviewedLinesCreateAnEmptyTarget() throws Exception {
        UUID source = create(List.of(line(category("Rent", "expense"), "1000.00")));

        copy(source, Map.of("targetMonth", "2028-02", "lines", List.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.totalPlanned").value(0.0));
    }

    @Test
    void reviewedLinesCanRemediateAnInvalidActiveSourceCategory() throws Exception {
        UUID invalid = category("Old utility", "expense");
        UUID replacement = category("Current utility", "both");
        UUID source = create(List.of(line(invalid, "90.00")));
        mockMvc.perform(post("/api/v1/categories/{id}/archive", invalid)).andExpect(status().isOk());

        copy(source, Map.of("targetMonth", "2028-02", "lines", List.of(line(replacement, "95.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].categoryId").value(replacement.toString()));
    }

    @Test
    void rejectsInvalidReviewedDraftsWithFieldErrorsAndRollsBack() throws Exception {
        UUID category = category("Rent", "expense");
        UUID source = create(List.of());
        copy(source, Map.of("targetMonth", "2028-02", "lines", List.of(
                        line(category, "1.00"), line(category, "2.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lines").exists());
        assertThat(repository.count()).isEqualTo(1);

        copy(source, Map.of("targetMonth", "2028-03", "lines", List.of(
                        Map.of("categoryId", category, "plannedAmount", new BigDecimal("-1.001")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['lines[0].plannedAmount']").exists());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsIneligibleOrForeignReviewedCategoriesWithoutPartialCopy() throws Exception {
        UUID archived = category("Archived", "expense");
        UUID income = category("Salary", "income");
        UUID foreign = category("Foreign", "expense");
        UUID source = create(List.of());
        mockMvc.perform(post("/api/v1/categories/{id}/archive", archived)).andExpect(status().isOk());

        copy(source, Map.of("targetMonth", "2028-02", "lines", List.of(line(archived, "1.00"))))
                .andExpect(status().isConflict());
        copy(source, Map.of("targetMonth", "2028-03", "lines", List.of(line(income, "1.00"))))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE transaction_category SET owner_id = ? WHERE id = ?", otherOwner(), foreign);
        copy(source, Map.of("targetMonth", "2028-04", "lines", List.of(line(foreign, "1.00"))))
                .andExpect(status().isNotFound());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsOccupiedMonthsAcrossCurrenciesAndLifecycleWithDeterministicExistingId() throws Exception {
        UUID source = create(List.of());
        UUID existing = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO budget (id, owner_id, name, currency, period_type, start_date, end_date,
                archived_at, version, created_at, updated_at)
                VALUES (?, ?, 'Existing', 'EUR', 'MONTHLY', DATE '2028-02-01', DATE '2028-02-29',
                CURRENT_TIMESTAMP, 0, TIMESTAMP '2020-01-01 00:00:00', CURRENT_TIMESTAMP)
                """, existing, currentUser.userId());
        repository.saveAndFlush(new Budget(UUID.randomUUID(), currentUser.userId(), "Later", "USD",
                LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
        copy(source, "2028-02").andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingBudgetId").value(existing.toString()))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        copy(source, "2026-08").andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingBudgetId").value(source.toString()));
        assertThat(repository.count()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"targetMonth\":null}", "{\"targetMonth\":\"\"}",
            "{\"targetMonth\":\"2028-13\"}", "{\"targetMonth\":\"2028-2\"}",
            "{\"targetMonth\":\"2028-02-01\"}", "{\"targetMonth\":\"0000-01\"}"})
    void rejectsInvalidMonthsWithFieldErrors(String body) throws Exception {
        UUID source = create(List.of());
        mockMvc.perform(post("/api/v1/budgets/{id}/copy", source)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetMonth").exists())
                .andExpect(jsonPath("$.existingBudgetId").doesNotExist());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsMissingAndForeignSourcesWithoutDisclosingBudgets() throws Exception {
        UUID otherOwner = otherOwner();
        Budget foreign = repository.saveAndFlush(new Budget(UUID.randomUUID(), otherOwner, "Private", "USD",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        copy(foreign.getId(), "2028-02").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.existingBudgetId").doesNotExist());
        copy(UUID.randomUUID(), "2028-02").andExpect(status().isNotFound());
        UUID source = create(List.of());
        repository.saveAndFlush(new Budget(UUID.randomUUID(), otherOwner, "Private target", "USD",
                LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
        copy(source, "2028-02").andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(currentUser.userId().toString()))
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void rejectsInvalidActiveCategoryWithoutPartialCopyOrHistoricalMutation() throws Exception {
        UUID valid = category("Rent", "expense");
        UUID invalid = category("Old utility", "both");
        UUID source = create(List.of(line(valid, "10.00"), line(invalid, "20.00")));
        JsonNode before = budget(source);
        mockMvc.perform(post("/api/v1/categories/{id}/archive", invalid)).andExpect(status().isOk());
        copy(source, "2028-02").andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM budget_line", Long.class)).isEqualTo(2);
        assertThat(budget(source)).isEqualTo(before);
        mockMvc.perform(post("/api/v1/categories/{id}/restore", invalid)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/categories/{id}", invalid)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicability\":\"income\"}"))
                .andExpect(status().isOk());
        copy(source, "2028-02").andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(budget(source)).isEqualTo(before);
    }

    @Test
    void rejectsForeignRetainedCategoryRatherThanReassigningIt() throws Exception {
        UUID category = category("Private", "expense");
        UUID source = create(List.of(line(category, "10.00")));
        jdbc.update("UPDATE transaction_category SET owner_id = ? WHERE id = ?", otherOwner(), category);
        copy(source, "2028-02").andExpect(status().isNotFound());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentCopiesFromDifferentSourcesCreateOnlyOneTarget() throws Exception {
        UUID first = create(List.of(line(category("Rent", "expense"), "1000.00")));
        UUID second = create(List.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(source -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                return copy(source, "2028-02").andReturn().getResponse();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var one = futures.get(0).get(10, TimeUnit.SECONDS);
            var two = futures.get(1).get(10, TimeUnit.SECONDS);
            assertThat(List.of(one.getStatus(), two.getStatus())).containsExactlyInAnyOrder(201, 409);
            var winner = one.getStatus() == 201 ? one : two;
            var loser = one.getStatus() == 409 ? one : two;
            assertThat(mapper.readTree(loser.getContentAsString()).get("existingBudgetId"))
                    .isEqualTo(mapper.readTree(winner.getContentAsString()).get("id"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM budget WHERE start_date = DATE '2028-02-01'", Long.class))
                    .isEqualTo(1);
        } finally {
            start.countDown();
        }
    }

    @Test
    void copyWaitsForAnInFlightBudgetCreationBeforeCheckingOccupancy() throws Exception {
        UUID source = create(List.of());
        UUID existingId = UUID.randomUUID();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = new TransactionTemplate(transactionManager).execute(transaction -> {
                writeLock.acquire(currentUser.userId());
                CountDownLatch started = new CountDownLatch(1);
                var future = executor.submit(() -> {
                    started.countDown();
                    return copy(source, "2028-02").andReturn().getResponse();
                });
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                    repository.saveAndFlush(new Budget(existingId, currentUser.userId(), "Concurrent", "USD",
                            LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
                    return future;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            });
            var response = pending.get(10, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(mapper.readTree(response.getContentAsString()).get("existingBudgetId").asText())
                    .isEqualTo(existingId.toString());
            assertThat(repository.count()).isEqualTo(2);
        }
    }

    @Test
    void calculatesTargetProgressFromItsOwnLedgerWithoutCopyingSourceActivity() throws Exception {
        UUID category = category("Rent", "expense");
        UUID source = create(List.of(line(category, "1000.00")));
        UUID account = id(mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Checking","type":"checking","currency":"USD",
                                 "openingDate":"2026-01-01","openingBalance":0}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (var entry : Map.of("2026-07-05", "45.00", "2026-08-05", "99.00").entrySet()) {
            mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("accountId", account, "categoryId", category,
                                    "transactionDate", entry.getKey(), "amount", new BigDecimal(entry.getValue()),
                                    "description", "Rent", "type", "expense"))))
                    .andExpect(status().isCreated());
        }
        UUID copied = id(copy(source, "2026-07").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(get("/api/v1/budgets/{id}/progress", source))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalActual").value(99.0));
        mockMvc.perform(get("/api/v1/budgets/{id}/progress", copied))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalActual").value(45.0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transaction", Long.class)).isEqualTo(2);
    }

    private UUID otherOwner() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, display_name) VALUES (?, 'Other')", id);
        return id;
    }

    private ResultActions copy(UUID source, String month) throws Exception {
        return copy(source, Map.of("targetMonth", month));
    }

    private ResultActions copy(UUID source, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/v1/budgets/{id}/copy", source)
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private UUID create(List<Map<String, Object>> lines) throws Exception {
        return id(mockMvc.perform(post("/api/v1/budgets").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Monthly fixed costs", "currency", "USD",
                                "startDate", "2026-08-01", "endDate", "2026-08-31", "lines", lines))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private UUID category(String name, String applicability) throws Exception {
        return id(mockMvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "applicability", applicability))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> line(UUID category, String amount) {
        return Map.of("categoryId", category, "plannedAmount", new BigDecimal(amount));
    }

    private JsonNode budget(UUID id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/budgets/{id}", id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID id(String json) {
        return UUID.fromString(mapper.readTree(json).get("id").asText());
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }
}
