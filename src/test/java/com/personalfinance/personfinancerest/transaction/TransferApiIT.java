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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferApiIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FinancialTransactionRepository transactionRepository;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private TransactionCategoryRepository categoryRepository;
    @Autowired private BalanceSnapshotRepository snapshotRepository;
    @Autowired private CurrentUserProvider currentUserProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

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
    void createsLinkedSameCurrencyLegsAndExcludesThemFromSummary() throws Exception {
        UUID source = createAccount("Checking", "USD", "100.00");
        UUID destination = createAccount("Savings", "USD", "50.00");

        String body = mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload(source, destination, "20.00", "20.00"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/transfers/.+")))
                .andExpect(jsonPath("$.sourceAccountId").value(source.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(destination.toString()))
                .andExpect(jsonPath("$.sourceAmount").value(20.0))
                .andExpect(jsonPath("$.destinationAmount").value(20.0))
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        assertBalance(source, "80.00");
        assertBalance(destination, "70.00");
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].transferId").value(transferId.toString()));
        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("type", "transfer_out")
                        .queryParam("accountId", source.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("transfer_out"))
                .andExpect(jsonPath("$.items[0].transferId").value(transferId.toString()));
        mockMvc.perform(get("/api/v1/transactions/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createsAndEditsCrossCurrencyTransfersWithExplicitAmounts() throws Exception {
        UUID source = createAccount("Checking", "USD", "100.00");
        UUID destination = createAccount("Travel", "EUR", "50.00");
        UUID transferId = createTransfer(payload(source, destination, "20.00", "18.50"));
        assertBalance(source, "80.00");
        assertBalance(destination, "68.50");

        mockMvc.perform(put("/api/v1/transfers/{transferId}", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload(source, destination, "10.00", "9.25"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceAmount").value(10.0))
                .andExpect(jsonPath("$.destinationAmount").value(9.25));
        assertBalance(source, "90.00");
        assertBalance(destination, "59.25");
    }

    @Test
    void validatesTransferShapeAndProtectsLegsFromStandaloneMutation() throws Exception {
        UUID source = createAccount("Checking", "USD", "100.00");
        UUID destination = createAccount("Savings", "USD", "50.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload(source, source, "20.00", "20.00"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload(source, destination, "20.00", "19.00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("equal")));

        UUID transferId = createTransfer(payload(source, destination, "20.00", "20.00"));
        UUID legId = transactionRepository.findAllByTransferIdAndOwnerIdOrderByType(
                transferId, currentUserProvider.userId()).getFirst().getId();
        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", legId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("/api/v1/transfers")));
    }

    @Test
    void softDeletesAndRestoresBothLegsAndScopesTransfersToTheirOwner() throws Exception {
        UUID source = createAccount("Checking", "USD", "100.00");
        UUID destination = createAccount("Savings", "USD", "50.00");
        UUID transferId = createTransfer(payload(source, destination, "20.00", "20.00"));

        mockMvc.perform(delete("/api/v1/transfers/{transferId}", transferId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("deleted"));
        assertBalance(source, "100.00");
        assertBalance(destination, "50.00");
        mockMvc.perform(get("/api/v1/transfers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/transfers").queryParam("status", "deleted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(transferId.toString()));
        mockMvc.perform(get("/api/v1/transfers").queryParam("status", "all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(transferId.toString()));

        mockMvc.perform(post("/api/v1/transfers/{transferId}/restore", transferId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        assertBalance(source, "80.00");
        assertBalance(destination, "70.00");
        mockMvc.perform(get("/api/v1/transfers/{transferId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createTransfer(Payload payload) throws Exception {
        String body = mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createAccount(String name, String currency, String openingBalance) {
        return accountRepository.saveAndFlush(new FinancialAccount(
                UUID.randomUUID(), currentUserProvider.userId(), name, AccountType.CHECKING, currency,
                TODAY.minusDays(30), new BigDecimal(openingBalance)
        )).getId();
    }

    private Payload payload(UUID source, UUID destination, String sourceAmount, String destinationAmount) {
        return new Payload(source, destination, sourceAmount, destinationAmount, TODAY,
                "Account transfer", "Transfer notes", "transfer-ref");
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private void assertBalance(UUID accountId, String expected) {
        assertThat(accountRepository.findById(accountId).orElseThrow().getCurrentBalance())
                .isEqualByComparingTo(expected);
    }

    private record Payload(
            UUID sourceAccountId, UUID destinationAccountId, String sourceAmount,
            String destinationAmount, LocalDate transactionDate, String description,
            String notes, String externalReference
    ) {
    }
}
