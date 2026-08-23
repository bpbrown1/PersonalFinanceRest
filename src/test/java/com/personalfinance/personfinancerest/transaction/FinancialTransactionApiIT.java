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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
                .andExpect(jsonPath("$[0].amount").value(15.25))
                .andExpect(jsonPath("$[0].balanceImpact").value(-15.25));
        assertBalance(accountId, "84.75");
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "deleted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
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
        return createAccount(currentUserProvider.userId(), name, openingBalance).getId();
    }

    private FinancialAccount createAccount(UUID ownerId, String name, String openingBalance) {
        return accountRepository.saveAndFlush(new FinancialAccount(
                UUID.randomUUID(), ownerId, name, AccountType.CHECKING, "USD",
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
}
