package com.personalfinance.personfinancerest.transaction;

import com.personalfinance.personfinancerest.account.balance.BalanceSnapshotRepository;
import com.personalfinance.personfinancerest.account.management.AccountType;
import com.personalfinance.personfinancerest.account.management.FinancialAccount;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialTransactionApiIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private TransactionCategoryRepository categoryRepository;

    @Autowired
    private BalanceSnapshotRepository snapshotRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearLedger() {
        jdbcTemplate.update("DELETE FROM budget_line");
        jdbcTemplate.update("DELETE FROM budget");
        transactionRepository.deleteAll();
        snapshotRepository.deleteAll();
        jdbcTemplate.update("UPDATE transaction_category SET parent_id = NULL");
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user WHERE id <> ?", currentUserProvider.userId());
    }

    @Test
    void recordsAndRetrievesIncomeWithOptionalDetails() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID categoryId = createCategory("Salary", "income");

        String response = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "25.50", TODAY, " Paycheck ", "income",
                                categoryId, " Employer ", " August pay ", " payroll-8 "))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.matchesPattern("/api/v1/transactions/.+")))
                .andExpect(jsonPath("$.ownerId").value(currentUserProvider.userId().toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.amount").value(25.50))
                .andExpect(jsonPath("$.balanceImpact").value(25.50))
                .andExpect(jsonPath("$.type").value("income"))
                .andExpect(jsonPath("$.description").value("Paycheck"))
                .andExpect(jsonPath("$.merchantPayee").value("Employer"))
                .andExpect(jsonPath("$.notes").value("August pay"))
                .andExpect(jsonPath("$.externalReference").value("payroll-8"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.deletedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID transactionId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId.toString()));
        assertBalance(accountId, "125.50");
    }

    @Test
    void expenseUsesAPositiveMagnitudeAndNegativeBalanceImpact() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");

        createTransaction(new Payload(accountId, "15.25", TODAY, "Groceries", "expense",
                null, null, null, null));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amount").value(15.25))
                .andExpect(jsonPath("$.items[0].balanceImpact").value(-15.25));
        assertBalance(accountId, "84.75");
    }

    @Test
    void summarizesActiveOwnedTransactionsByAccountCurrency() throws Exception {
        UUID usdAccountId = createAccount("Checking", "USD", "100.00");
        UUID eurAccountId = createAccount("Travel", "EUR", "100.00");
        createTransaction(new Payload(usdAccountId, "2416.00", TODAY.minusDays(2), "Pay", "income",
                null, null, null, null));
        createTransaction(new Payload(usdAccountId, "24.36", TODAY.minusDays(1), "Lunch", "expense",
                null, null, null, null));
        createTransaction(new Payload(eurAccountId, "10.00", TODAY.minusDays(1), "Train", "expense",
                null, null, null, null));
        UUID deletedId = createTransaction(new Payload(
                usdAccountId, "999.00", TODAY.minusDays(1), "Deleted", "income",
                null, null, null, null
        ));
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", deletedId))
                .andExpect(status().isOk());

        UUID otherOwnerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, display_name) VALUES (?, ?)", otherOwnerId, "Other User");
        FinancialAccount otherAccount = createAccount(otherOwnerId, "Private", "500.00");
        transactionRepository.saveAndFlush(new FinancialTransaction(
                UUID.randomUUID(), otherOwnerId, otherAccount.getId(), null, new BigDecimal("500.00"),
                TransactionType.INCOME, TODAY, "Private", null, null, null
        ));

        mockMvc.perform(get("/api/v1/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].income").value(0.0))
                .andExpect(jsonPath("$[0].spending").value(10.0))
                .andExpect(jsonPath("$[0].netImpact").value(-10.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].income").value(2416.0))
                .andExpect(jsonPath("$[1].spending").value(24.36))
                .andExpect(jsonPath("$[1].netImpact").value(2391.64))
                .andExpect(jsonPath("$[1].transactionCount").value(2));
    }

    @Test
    void appliesInclusiveOpenEndedSummaryDatesAndValidatesTheRange() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        createTransaction(new Payload(accountId, "20.00", TODAY.minusDays(2), "Older", "income",
                null, null, null, null));
        createTransaction(new Payload(accountId, "5.00", TODAY.minusDays(1), "Newer", "expense",
                null, null, null, null));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("from", TODAY.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].income").value(0.0))
                .andExpect(jsonPath("$[0].spending").value(5.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("to", TODAY.minusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].income").value(20.0))
                .andExpect(jsonPath("$[0].spending").value(0.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("from", TODAY.minusDays(1).toString())
                        .queryParam("to", TODAY.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionCount").value(1));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("from", TODAY.toString())
                        .queryParam("to", TODAY.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.dateRange").value("from must be on or before to"));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.from").exists());

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("from", TODAY.minusDays(10).toString())
                        .queryParam("to", TODAY.minusDays(9).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void combinesAccountDateCategoryTypeAmountAndTextFilters() throws Exception {
        UUID checkingId = createAccount("Checking", "100.00");
        UUID cashId = createAccount("Cash", "50.00");
        UUID diningId = createCategory("Dining", "expense");
        createTransaction(new Payload(
                checkingId, "30.00", TODAY.minusDays(3), "Team dinner", "expense",
                diningId, "Cafe", "Project celebration", "meal-30"
        ));
        createTransaction(new Payload(
                checkingId, "15.00", TODAY.minusDays(2), "Quick lunch", "expense",
                diningId, "Deli", null, "meal-15"
        ));
        createTransaction(new Payload(
                cashId, "25.00", TODAY.minusDays(1), "Cash refund", "income",
                null, null, null, "refund-25"
        ));

        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("accountId", checkingId.toString())
                        .queryParam("from", TODAY.minusDays(3).toString())
                        .queryParam("to", TODAY.minusDays(2).toString())
                        .queryParam("categoryId", diningId.toString())
                        .queryParam("type", "expense")
                        .queryParam("minAmount", "20.00")
                        .queryParam("maxAmount", "30.00")
                        .queryParam("text", "CELEBRATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Team dinner"))
                .andExpect(jsonPath("$.items[0].amount").value(30.0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.sortBy").value("date"))
                .andExpect(jsonPath("$.sortDirection").value("desc"));
    }

    @Test
    void paginatesAndSortsByAmountOrDateWithStableMetadata() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        createTransaction(new Payload(accountId, "30.00", TODAY.minusDays(3), "Thirty", "expense",
                null, null, null, null));
        createTransaction(new Payload(accountId, "10.00", TODAY.minusDays(1), "Ten", "expense",
                null, null, null, null));
        createTransaction(new Payload(accountId, "20.00", TODAY.minusDays(2), "Twenty", "expense",
                null, null, null, null));

        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .queryParam("sort", "amount")
                        .queryParam("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].amount").value(20.0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.sortBy").value("amount"))
                .andExpect(jsonPath("$.sortDirection").value("asc"));

        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("sort", "date")
                        .queryParam("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").value("Thirty"));
    }

    @Test
    void validatesSearchRangesPagingSortingTypesAndMalformedValues() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("from", TODAY.toString())
                        .queryParam("to", TODAY.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateRange").exists());
        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("minAmount", "20.00").queryParam("maxAmount", "10.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amountRange").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.page").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.size").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("sort", "description"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.sort").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("direction", "sideways"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.sort").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("type", "refund"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.type").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("accountId", "not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.accountId").exists());
        mockMvc.perform(get("/api/v1/transactions").queryParam("minAmount", "not-a-number"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.minAmount").exists());
    }

    @Test
    void appliesAccountCategoryAndTypeFiltersToSummaries() throws Exception {
        UUID checkingId = createAccount("Checking", "100.00");
        UUID savingsId = createAccount("Savings", "100.00");
        UUID salaryId = createCategory("Salary", "income");
        UUID diningId = createCategory("Dining", "expense");
        createTransaction(new Payload(checkingId, "100.00", TODAY, "Pay", "income",
                salaryId, null, null, null));
        createTransaction(new Payload(checkingId, "20.00", TODAY, "Dinner", "expense",
                diningId, null, null, null));
        createTransaction(new Payload(savingsId, "5.00", TODAY, "Interest", "income",
                salaryId, null, null, null));

        mockMvc.perform(get("/api/v1/transactions/summary")
                        .queryParam("accountId", checkingId.toString())
                        .queryParam("categoryId", salaryId.toString())
                        .queryParam("type", "income"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].income").value(100.0))
                .andExpect(jsonPath("$[0].spending").value(0.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1));

        mockMvc.perform(get("/api/v1/transactions/summary").queryParam("type", "transfer_out"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validatesRequiredFieldsMagnitudeAndTransactionType() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"description\":\" \",\"type\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.transactionDate").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"transfer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request body is malformed"));
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void replacesAllFieldsAndAppliesOnlyTheNetBalanceDelta() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID categoryId = createCategory("Dining", "expense");
        UUID transactionId = createTransaction(new Payload(accountId, "10.00", TODAY, "Lunch", "expense",
                categoryId, "Cafe", "Team lunch", "receipt-1"));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "30.00", TODAY, "Refund", "income",
                                null, " ", null, " "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30.0))
                .andExpect(jsonPath("$.balanceImpact").value(30.0))
                .andExpect(jsonPath("$.type").value("income"))
                .andExpect(jsonPath("$.categoryId").isEmpty())
                .andExpect(jsonPath("$.merchantPayee").isEmpty())
                .andExpect(jsonPath("$.notes").isEmpty())
                .andExpect(jsonPath("$.externalReference").isEmpty());

        assertBalance(accountId, "130.00");
    }

    @Test
    void createsSearchesAndSummarizesAnOrderedSplitTransaction() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID diningId = createCategory("Dining", "expense");

        String response = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "100.00", "expense", null, List.of(
                                new SplitRow(null, groceriesId, "60.00"),
                                new SplitRow(null, diningId, "40.00")
                        )))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").isEmpty())
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andExpect(jsonPath("$.splits[0].position").value(0))
                .andExpect(jsonPath("$.splits[0].categoryId").value(groceriesId.toString()))
                .andExpect(jsonPath("$.splits[0].amount").value(60.0))
                .andExpect(jsonPath("$.splits[0].id").isNotEmpty())
                .andExpect(jsonPath("$.splits[1].position").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID transactionId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        mockMvc.perform(get("/api/v1/transactions").queryParam("categoryId", diningId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(transactionId.toString()))
                .andExpect(jsonPath("$.items[0].splits.length()").value(2));

        mockMvc.perform(get("/api/v1/transactions/summary").queryParam("categoryId", groceriesId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spending").value(60.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1));
        mockMvc.perform(get("/api/v1/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spending").value(100.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1));
        assertBalance(accountId, "0.00");
    }

    @Test
    void replacesSplitRowsByIdAndCanReturnToASingleCategory() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID diningId = createCategory("Dining", "expense");
        UUID travelId = createCategory("Travel", "expense");
        String created = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(null, groceriesId, "30.00"),
                                new SplitRow(null, diningId, "20.00")
                        )))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID transactionId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        UUID retainedSplitId = UUID.fromString(objectMapper.readTree(created)
                .get("splits").get(1).get("id").asText());

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(retainedSplitId, diningId, "15.00"),
                                new SplitRow(null, travelId, "35.00")
                        )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andExpect(jsonPath("$.splits[0].id").value(retainedSplitId.toString()))
                .andExpect(jsonPath("$.splits[0].position").value(0))
                .andExpect(jsonPath("$.splits[1].categoryId").value(travelId.toString()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_split WHERE transaction_id = ?", Integer.class, transactionId
        )).isEqualTo(2);

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "50.00", TODAY, "Single category", "expense",
                                groceriesId, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(groceriesId.toString()))
                .andExpect(jsonPath("$.splits.length()").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_split WHERE transaction_id = ?", Integer.class, transactionId
        )).isZero();
    }

    @Test
    void returnsIndexedSplitValidationErrorsWithoutPartialPersistence() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID diningId = createCategory("Dining", "expense");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", groceriesId, List.of(
                                new SplitRow(UUID.randomUUID(), groceriesId, "20.00"),
                                new SplitRow(null, groceriesId, "20.00")
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists())
                .andExpect(jsonPath("$.fieldErrors.splits").exists())
                .andExpect(jsonPath("$.fieldErrors['splits[0].id']").exists());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(null, groceriesId, "20.00"),
                                new SplitRow(null, diningId, "20.00")
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.splits")
                        .value("Split amounts must exactly equal the transaction amount"));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(null, UUID.randomUUID(), "25.00"),
                                new SplitRow(null, diningId, "25.00")
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['splits[0].categoryId']").exists());
        assertThat(transactionRepository.count()).isZero();
        assertBalance(accountId, "100.00");
    }

    @Test
    void retainsSplitsAcrossDeleteRestoreAndAllowsUnchangedArchivedRows() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID diningId = createCategory("Dining", "expense");
        String created = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "30.00", "expense", null, List.of(
                                new SplitRow(null, groceriesId, "10.00"),
                                new SplitRow(null, diningId, "20.00")
                        )))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID transactionId = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        UUID groceriesSplitId = UUID.fromString(objectMapper.readTree(created)
                .get("splits").get(0).get("id").asText());
        UUID diningSplitId = UUID.fromString(objectMapper.readTree(created)
                .get("splits").get(1).get("id").asText());
        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", groceriesId)).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "30.00", "expense", null, List.of(
                                new SplitRow(groceriesSplitId, groceriesId, "10.00"),
                                new SplitRow(diningSplitId, diningId, "20.00")
                        )))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits.length()").value(2));
        mockMvc.perform(post("/api/v1/transactions/{transactionId}/restore", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[0].id").value(groceriesSplitId.toString()));
        assertBalance(accountId, "70.00");
    }

    @Test
    void rejectsSplitIdsFromAnotherTransactionAndNewArchivedAssociationsAtomically() throws Exception {
        UUID accountId = createAccount("Checking", "200.00");
        UUID groceriesId = createCategory("Groceries", "expense");
        UUID diningId = createCategory("Dining", "expense");
        UUID travelId = createCategory("Travel", "expense");
        String first = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(null, groceriesId, "30.00"),
                                new SplitRow(null, diningId, "20.00")
                        )))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "40.00", "expense", null, List.of(
                                new SplitRow(null, diningId, "10.00"),
                                new SplitRow(null, travelId, "30.00")
                        )))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID firstTransactionId = UUID.fromString(objectMapper.readTree(first).get("id").asText());
        UUID firstSplitId = UUID.fromString(objectMapper.readTree(first).get("splits").get(0).get("id").asText());
        UUID foreignSplitId = UUID.fromString(objectMapper.readTree(second).get("splits").get(0).get("id").asText());

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", firstTransactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(firstSplitId, groceriesId, "30.00"),
                                new SplitRow(foreignSplitId, diningId, "20.00")
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['splits[1].id']")
                        .value("Split id does not belong to this transaction"));

        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", travelId)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/transactions/{transactionId}", firstTransactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(splitPayload(accountId, "50.00", "expense", null, List.of(
                                new SplitRow(firstSplitId, groceriesId, "30.00"),
                                new SplitRow(null, travelId, "20.00")
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['splits[1].categoryId']")
                        .value("An archived category cannot be assigned"));

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", firstTransactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[0].amount").value(30.0))
                .andExpect(jsonPath("$.splits[1].amount").value(20.0));
        assertBalance(accountId, "110.00");
    }

    @Test
    void movesATransactionBetweenAccountsAndUpdatesBothBalances() throws Exception {
        UUID checkingId = createAccount("Checking", "100.00");
        UUID cashId = createAccount("Cash", "50.00");
        UUID transactionId = createTransaction(new Payload(checkingId, "20.00", TODAY, "Dinner", "expense",
                null, null, null, null));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(cashId, "20.00", TODAY, "Dinner", "expense",
                                null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(cashId.toString()));

        assertBalance(checkingId, "100.00");
        assertBalance(cashId, "30.00");
    }

    @Test
    void softDeleteAndRestoreAreRecoverableIdempotentAndFilterable() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID transactionId = createTransaction(new Payload(accountId, "20.00", TODAY, "Dinner", "expense",
                null, null, null, null));

        String deletedAt = objectMapper.readTree(mockMvc.perform(
                                delete("/api/v1/transactions/{transactionId}", transactionId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("deleted"))
                        .andExpect(jsonPath("$.deletedAt").isNotEmpty())
                        .andReturn().getResponse().getContentAsString())
                .get("deletedAt").asText();
        assertBalance(accountId, "100.00");

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").value(deletedAt));
        assertBalance(accountId, "100.00");

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "deleted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "archived"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());

        mockMvc.perform(post("/api/v1/transactions/{transactionId}/restore", transactionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(post("/api/v1/transactions/{transactionId}/restore", transactionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        assertBalance(accountId, "80.00");
    }

    @Test
    void enforcesAccountDateAndCategoryRules() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID incomeCategoryId = createCategory("Salary", "income");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "10.00", TODAY, "Wrong category", "expense",
                                incomeCategoryId, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("incompatible")));

        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", incomeCategoryId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "10.00", TODAY, "Old category", "income",
                                incomeCategoryId, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("archived category")));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "10.00", TODAY.plusDays(1), "Future", "expense",
                                null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("future")));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(accountId, "10.00", TODAY, "Archived", "expense",
                                null, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("archived financial account")));
    }

    @Test
    void scopesTransactionsAndAssociationsToTheCurrentOwner() throws Exception {
        UUID otherOwnerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, display_name) VALUES (?, ?)", otherOwnerId, "Other User");
        FinancialAccount otherAccount = createAccount(otherOwnerId, "Private", "200.00");
        FinancialTransaction otherTransaction = transactionRepository.saveAndFlush(new FinancialTransaction(
                UUID.randomUUID(), otherOwnerId, otherAccount.getId(), null, new BigDecimal("5.00"),
                TransactionType.EXPENSE, TODAY, "Private", null, null, null
        ));

        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/transactions/{transactionId}", otherTransaction.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/transactions/{transactionId}", otherTransaction.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(otherAccount.getId(), "10.00", TODAY, "No", "expense",
                                null, null, null, null))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Payload(otherAccount.getId(), "10.00", TODAY, "No", "expense",
                                null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void retainedDeletedTransactionsStillProtectAccountFinancialTerms() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID transactionId = createTransaction(new Payload(accountId, "20.00", TODAY, "Dinner", "expense",
                null, null, null, null));
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", transactionId)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/accounts/{accountId}", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingBalance\":150.00}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/accounts/{accountId}", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsCorsPreflightForPutAndDeleteFromTheAngularOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/transactions/{transactionId}", UUID.randomUUID())
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("PUT"),
                                org.hamcrest.Matchers.containsString("DELETE"))));
    }

    private UUID createTransaction(Payload payload) throws Exception {
        String response = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createAccount(String name, String openingBalance) {
        return createAccount(name, "USD", openingBalance);
    }

    private UUID createAccount(String name, String currency, String openingBalance) {
        return createAccount(currentUserProvider.userId(), name, currency, openingBalance).getId();
    }

    private FinancialAccount createAccount(UUID ownerId, String name, String openingBalance) {
        return createAccount(ownerId, name, "USD", openingBalance);
    }

    private FinancialAccount createAccount(UUID ownerId, String name, String currency, String openingBalance) {
        return accountRepository.saveAndFlush(new FinancialAccount(
                UUID.randomUUID(), ownerId, name, AccountType.CHECKING, currency,
                TODAY.minusDays(30), new BigDecimal(openingBalance)
        ));
    }

    private UUID createCategory(String name, String applicability) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"applicability\":\"" + applicability + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private SplitPayload splitPayload(UUID accountId, String amount, String type, UUID categoryId,
                                      List<SplitRow> splits) {
        return new SplitPayload(
                accountId, amount, TODAY, "Split transaction", type, categoryId, splits,
                null, null, null
        );
    }

    private void assertBalance(UUID accountId, String expected) {
        assertThat(accountRepository.findById(accountId).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo(expected);
    }

    private record Payload(
            UUID accountId,
            String amount,
            LocalDate transactionDate,
            String description,
            String type,
            UUID categoryId,
            String merchantPayee,
            String notes,
            String externalReference
    ) {
    }

    private record SplitPayload(
            UUID accountId,
            String amount,
            LocalDate transactionDate,
            String description,
            String type,
            UUID categoryId,
            List<SplitRow> splits,
            String merchantPayee,
            String notes,
            String externalReference
    ) {
    }

    private record SplitRow(UUID id, UUID categoryId, String amount) {
    }
}
