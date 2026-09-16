package com.personalfinance.personfinancerest.account.reconciliation;

import com.personalfinance.personfinancerest.account.balance.BalanceSnapshotRepository;
import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.category.TransactionCategoryRepository;
import com.personalfinance.personfinancerest.transaction.FinancialTransactionRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountReconciliationApiIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate STATEMENT_DATE = TODAY.minusDays(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountReconciliationRepository reconciliationRepository;

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
    void clearData() {
        jdbcTemplate.update("DELETE FROM recurring_expense_match");
        jdbcTemplate.update("DELETE FROM recurring_expense");
        jdbcTemplate.update("DELETE FROM budget_line");
        jdbcTemplate.update("DELETE FROM budget");
        reconciliationRepository.deleteAll();
        transactionRepository.deleteAll();
        snapshotRepository.deleteAll();
        jdbcTemplate.update("UPDATE transaction_category SET parent_id = NULL");
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user WHERE id <> ?", currentUserProvider.userId());
    }

    @Test
    void previewsAsOfBalanceAndConfirmsOneIdempotentAuditableAdjustment() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID incomeCategoryId = createCategory("Interest Income", "income");
        createTransaction(accountId, "25.00", STATEMENT_DATE, "Posted interest", "income", incomeCategoryId,
                "imported");
        createTransaction(accountId, "10.00", TODAY, "Future relative to statement", "expense", null,
                "manual");

        JsonNode preview = preview(accountId, "130.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedBalance").value(125.0))
                .andExpect(jsonPath("$.difference").value(5.0))
                .andExpect(jsonPath("$.adjustmentType").value("income"))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andReturnJson();

        String confirmation = """
                {
                  "statementDate": "%s",
                  "statementBalance": 130.00,
                  "ledgerToken": "%s",
                  "categoryId": "%s",
                  "explanation": "Statement includes an institution adjustment"
                }
                """.formatted(STATEMENT_DATE, preview.get("ledgerToken").asText(), incomeCategoryId);

        String firstResponse = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header("Idempotency-Key", "statement-2026-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.difference").value(5.0))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID reconciliationId = UUID.fromString(objectMapper.readTree(firstResponse).get("id").asText());
        UUID adjustmentId = UUID.fromString(objectMapper.readTree(firstResponse).get("transactionId").asText());

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header("Idempotency-Key", "statement-2026-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reconciliationId.toString()))
                .andExpect(jsonPath("$.transactionId").value(adjustmentId.toString()));

        assertThat(reconciliationRepository.count()).isOne();
        assertThat(transactionRepository.count()).isEqualTo(3);
        assertThat(accountRepository.findById(accountId).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("120.00");

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", adjustmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance").value("reconciliation"))
                .andExpect(jsonPath("$.reconciliationId").value(reconciliationId.toString()));
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", adjustmentId))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].transactionId").value(adjustmentId.toString()));
    }

    @Test
    void rejectsAStalePreviewWithoutCreatingAnAdjustment() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID incomeCategoryId = createCategory("Interest Income", "income");
        JsonNode preview = preview(accountId, "110.00").andExpect(status().isOk()).andReturnJson();
        createTransaction(accountId, "1.00", STATEMENT_DATE, "Late activity", "income", incomeCategoryId,
                "manual");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header("Idempotency-Key", "stale-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statementDate": "%s",
                                  "statementBalance": 110.00,
                                  "ledgerToken": "%s",
                                  "categoryId": "%s",
                                  "explanation": "Expected adjustment"
                                }
                                """.formatted(STATEMENT_DATE, preview.get("ledgerToken").asText(),
                                incomeCategoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "The reconciliation preview is stale; refresh it before confirming"));

        assertThat(reconciliationRepository.count()).isZero();
        assertThat(transactionRepository.count()).isOne();
    }

    @Test
    void recordsAZeroDifferenceWithoutCreatingATransaction() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        JsonNode preview = preview(accountId, "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difference").value(0.0))
                .andExpect(jsonPath("$.adjustmentType").isEmpty())
                .andReturnJson();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header("Idempotency-Key", "balanced-statement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statementDate": "%s",
                                  "statementBalance": 100.00,
                                  "ledgerToken": "%s"
                                }
                                """.formatted(STATEMENT_DATE, preview.get("ledgerToken").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.difference").value(0.0))
                .andExpect(jsonPath("$.transactionId").isEmpty());

        assertThat(reconciliationRepository.count()).isOne();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void exposesManualAndImportedProvenanceButReservesReconciliationProvenance() throws Exception {
        UUID accountId = createAccount("Checking", "100.00");
        UUID categoryId = createCategory("Interest Income", "income");

        createTransaction(accountId, "1.25", STATEMENT_DATE, "Imported interest", "income", categoryId,
                "imported");
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].provenance").value("imported"));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(accountId, "1.00", STATEMENT_DATE,
                                "Forged adjustment", "income", categoryId, "reconciliation")))
                .andExpect(status().isConflict());
    }

    @Test
    void anExplicitPaymentReducesASignedLiabilityBalance() throws Exception {
        UUID checkingId = createAccount("Checking", "checking", "500.00");
        UUID creditCardId = createAccount("Credit card", "credit_card", "0.00");
        UUID feeCategoryId = createCategory("Card charges", "expense");
        createTransaction(creditCardId, "100.00", TODAY, "Card charge", "expense", feeCategoryId,
                "manual");

        assertThat(accountRepository.findById(creditCardId).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("-100.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "sourceAmount": 100.00,
                                  "destinationAmount": 100.00,
                                  "transactionDate": "%s",
                                  "description": "Credit card payment"
                                }
                                """.formatted(checkingId, creditCardId, TODAY)))
                .andExpect(status().isCreated());

        assertThat(accountRepository.findById(creditCardId).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("0.00");
        assertThat(accountRepository.findById(checkingId).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo("400.00");
    }

    private Result preview(UUID accountId, String statementBalance) throws Exception {
        return new Result(mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations/preview", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "statementDate": "%s",
                          "statementBalance": %s
                        }
                        """.formatted(STATEMENT_DATE, statementBalance))));
    }

    private UUID createAccount(String name, String openingBalance) throws Exception {
        return createAccount(name, "checking", openingBalance);
    }

    private UUID createAccount(String name, String type, String openingBalance) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "type": "%s",
                                  "currency": "USD",
                                  "openingDate": "%s",
                                  "openingBalance": %s
                                }
                                """.formatted(name, type, TODAY.minusDays(30), openingBalance)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createCategory(String name, String applicability) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","applicability":"%s"}
                                """.formatted(name, applicability)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void createTransaction(UUID accountId, String amount, LocalDate date, String description,
                                   String type, UUID categoryId, String provenance) throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId, amount, date, description, type, categoryId, provenance
                        )))
                .andExpect(status().isCreated());
    }

    private String transactionJson(UUID accountId, String amount, LocalDate date, String description,
                                   String type, UUID categoryId, String provenance) {
        String category = categoryId == null ? "null" : "\"" + categoryId + "\"";
        return """
                {
                  "accountId": "%s",
                  "amount": %s,
                  "transactionDate": "%s",
                  "description": "%s",
                  "type": "%s",
                  "provenance": "%s",
                  "categoryId": %s
                }
                """.formatted(accountId, amount, date, description, type, provenance, category);
    }

    private final class Result {
        private final org.springframework.test.web.servlet.ResultActions actions;

        private Result(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        private Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        private JsonNode andReturnJson() throws Exception {
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
