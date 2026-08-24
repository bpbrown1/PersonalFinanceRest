package com.personalfinance.personfinancerest.budget;

import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BudgetApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private TransactionCategoryRepository categoryRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearBudgets() {
        jdbcTemplate.update("DELETE FROM budget_line");
        jdbcTemplate.update("DELETE FROM budget");
        jdbcTemplate.update("UPDATE transaction_category SET parent_id = NULL");
        categoryRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user WHERE id <> ?", currentUserProvider.userId());
    }

    @Test
    void createsListsAndRetrievesAMonthlyBudgetWithActiveLineTotal() throws Exception {
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID householdId = createCategory("Household", "both");

        String created = mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BudgetPayload(
                                " August Plan ", "usd", date(1), date(31), List.of(
                                new LinePayload(groceriesId, new BigDecimal("125.50")),
                                new LinePayload(householdId, new BigDecimal("24.50"))
                        )))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/budgets/.+")))
                .andExpect(jsonPath("$.ownerId").value(currentUserProvider.userId().toString()))
                .andExpect(jsonPath("$.name").value("August Plan"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.periodType").value("monthly"))
                .andExpect(jsonPath("$.totalPlanned").value(150.0))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].position").value(0))
                .andExpect(jsonPath("$.lines[1].position").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID budgetId = id(created);

        mockMvc.perform(get("/api/v1/budgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(budgetId.toString()));
        mockMvc.perform(get("/api/v1/budgets/{budgetId}", budgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].categoryId").value(groceriesId.toString()));
    }

    @Test
    void validatesMonthlyPeriodsAmountsAndUniqueExpenseCategories() throws Exception {
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID incomeId = createCategory("Salary", "income");

        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BudgetPayload("Bad period", "USD", date(2), date(30), List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());

        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BudgetPayload("Duplicate", "USD", date(1), date(31), List.of(
                                new LinePayload(groceriesId, new BigDecimal("10.00")),
                                new LinePayload(groceriesId, new BigDecimal("20.00"))
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lines").exists());

        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BudgetPayload("Income", "USD", date(1), date(31), List.of(
                                new LinePayload(incomeId, new BigDecimal("10.00"))
                        )))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invalid\",\"currency\":\"US\",\"startDate\":\"2026-08-01\","
                                + "\"endDate\":\"2026-08-31\",\"lines\":[{\"categoryId\":\""
                                + groceriesId + "\",\"plannedAmount\":-1.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists())
                .andExpect(jsonPath("$.fieldErrors['lines[0].plannedAmount']").exists());
        assertThat(budgetRepository.count()).isZero();
    }

    @Test
    void updatesHistoricalBudgetsAndRequiresRestoreBeforeArchivedMutation() throws Exception {
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID budgetId = createBudget("Historical", List.of(new LinePayload(groceriesId, amount("50.00"))));

        mockMvc.perform(put("/api/v1/budgets/{budgetId}", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new MetadataPayload("Corrected", "eur", date(1), date(31)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corrected"))
                .andExpect(jsonPath("$.currency").value("EUR"));

        String archived = mockMvc.perform(post("/api/v1/budgets/{budgetId}/archive", budgetId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("archived"))
                .andReturn().getResponse().getContentAsString();
        String archivedAt = objectMapper.readTree(archived).get("archivedAt").asText();
        mockMvc.perform(post("/api/v1/budgets/{budgetId}/archive", budgetId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").value(archivedAt));
        mockMvc.perform(put("/api/v1/budgets/{budgetId}", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new MetadataPayload("Blocked", "USD", date(1), date(31)))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/budgets/{budgetId}/lines", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LinePayload(UUID.randomUUID(), amount("1.00")))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/budgets/{budgetId}/restore", budgetId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(put("/api/v1/budgets/{budgetId}", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new MetadataPayload("Restored", "USD", date(1), date(31)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Restored"));
    }

    @Test
    void addsUpdatesReordersArchivesAndRestoresRetainedLines() throws Exception {
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID diningId = createCategory("Dining", "expense");
        UUID travelId = createCategory("Travel", "expense");
        UUID budgetId = createBudget("August", List.of(
                new LinePayload(groceriesId, amount("100.00")),
                new LinePayload(diningId, amount("50.00"))
        ));
        JsonNode initial = budget(budgetId);
        UUID groceriesLineId = UUID.fromString(initial.get("lines").get(0).get("id").asText());
        UUID diningLineId = UUID.fromString(initial.get("lines").get(1).get("id").asText());

        String added = mockMvc.perform(post("/api/v1/budgets/{budgetId}/lines", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LinePayload(travelId, amount("25.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlanned").value(175.0))
                .andReturn().getResponse().getContentAsString();
        UUID travelLineId = UUID.fromString(objectMapper.readTree(added).get("lines").get(2).get("id").asText());

        mockMvc.perform(put("/api/v1/budgets/{budgetId}/lines/{lineId}", budgetId, diningLineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LinePayload(diningId, amount("75.00")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPlanned").value(200.0));
        mockMvc.perform(post("/api/v1/budgets/{budgetId}/lines", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LinePayload(groceriesId, amount("1.00")))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/budgets/{budgetId}/lines/reorder", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReorderPayload(List.of(travelLineId, groceriesLineId, diningLineId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].id").value(travelLineId.toString()))
                .andExpect(jsonPath("$.lines[2].id").value(diningLineId.toString()));

        mockMvc.perform(post("/api/v1/budgets/{budgetId}/lines/{lineId}/archive", budgetId, groceriesLineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlanned").value(100.0));
        mockMvc.perform(post("/api/v1/budgets/{budgetId}/lines/{lineId}/restore", budgetId, groceriesLineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlanned").value(200.0));

        mockMvc.perform(put("/api/v1/budgets/{budgetId}/lines/reorder", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReorderPayload(List.of(groceriesLineId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lineIds").exists());
    }

    @Test
    void retainsArchivedCategoryAssociationsForHistoricalMeaning() throws Exception {
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID budgetId = createBudget("August", List.of(new LinePayload(groceriesId, amount("50.00"))));
        UUID lineId = UUID.fromString(budget(budgetId).get("lines").get(0).get("id").asText());
        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", groceriesId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/budgets/{budgetId}", budgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].categoryId").value(groceriesId.toString()));
        mockMvc.perform(put("/api/v1/budgets/{budgetId}/lines/{lineId}", budgetId, lineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LinePayload(groceriesId, amount("75.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlanned").value(75.0));
    }

    @Test
    void scopesBudgetsLinesAndCategoriesToTheCurrentOwner() throws Exception {
        UUID otherOwnerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, display_name) VALUES (?, ?)", otherOwnerId, "Other");
        UUID otherCategoryId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO transaction_category
                (id, owner_id, name, normalized_name, active_name_key, applicability, created_at, updated_at)
                VALUES (?, ?, 'Private', 'private', 'private', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, otherCategoryId, otherOwnerId);
        Budget otherBudget = budgetRepository.saveAndFlush(new Budget(
                UUID.randomUUID(), otherOwnerId, "Private", "USD", date(1), date(31)
        ));

        mockMvc.perform(get("/api/v1/budgets").queryParam("status", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/budgets/{budgetId}", otherBudget.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BudgetPayload("No", "USD", date(1), date(31), List.of(
                                new LinePayload(otherCategoryId, amount("1.00"))
                        )))))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersLifecycleAndRejectsUnsupportedStatus() throws Exception {
        UUID budgetId = createBudget("August", List.of());
        mockMvc.perform(post("/api/v1/budgets/{budgetId}/archive", budgetId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/budgets"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/budgets").queryParam("status", "archived"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/budgets").queryParam("status", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/budgets").queryParam("status", "closed"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    private UUID createBudget(String name, List<LinePayload> lines) throws Exception {
        String response = mockMvc.perform(post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BudgetPayload(name, "USD", date(1), date(31), lines))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return id(response);
    }

    private JsonNode budget(UUID budgetId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/budgets/{budgetId}", budgetId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID createCategory(String name, String applicability) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"applicability\":\"" + applicability + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return id(response);
    }

    private UUID id(String response) {
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 8, day);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private record BudgetPayload(String name, String currency, LocalDate startDate, LocalDate endDate,
                                 List<LinePayload> lines) {
    }

    private record MetadataPayload(String name, String currency, LocalDate startDate, LocalDate endDate) {
    }

    private record LinePayload(UUID categoryId, BigDecimal plannedAmount) {
    }

    private record ReorderPayload(List<UUID> lineIds) {
    }
}
