package com.personalfinance.personfinancerest.account.balance;

import com.personalfinance.personfinancerest.account.management.FinancialAccountRepository;
import com.personalfinance.personfinancerest.transaction.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountBalanceApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BalanceSnapshotRepository snapshotRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @BeforeEach
    void clearAccounts() {
        transactionRepository.deleteAll();
        snapshotRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void accountCreationRecordsAnOpeningSnapshot() throws Exception {
        UUID accountId = createAccount();

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance-snapshots", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].balance").value(1250.75))
                .andExpect(jsonPath("$[0].effectiveAt").value("2026-08-20T00:00:00Z"))
                .andExpect(jsonPath("$[0].source").value("opening"));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .queryParam("asOf", "2026-08-20T06:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1250.75))
                .andExpect(jsonPath("$.source").value("opening"));
    }

    @Test
    void retainsMultipleSnapshotsAndRetrievesBalancesAsOfAnInstant() throws Exception {
        UUID accountId = createAccount();
        UUID newestSnapshotId = createSnapshot(accountId, "1800.00", "2026-08-21T12:00:00Z");
        createSnapshot(accountId, "1300.00", "2026-08-20T12:00:00Z");

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(1250.75))
                .andExpect(jsonPath("$.currentBalance").value(1800.00));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance-snapshots", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].balance").value(1250.75))
                .andExpect(jsonPath("$[1].balance").value(1300.00))
                .andExpect(jsonPath("$[2].balance").value(1800.00));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .queryParam("asOf", "2026-08-20T18:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1300.00));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance-snapshots/{snapshotId}",
                        accountId, newestSnapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newestSnapshotId.toString()));
    }

    @Test
    void rejectsDuplicateOrFutureSnapshots() throws Exception {
        UUID accountId = createAccount();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/balance-snapshots", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": 1300.00,
                                  "effectiveAt": "2026-08-20T00:00:00Z"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/balance-snapshots", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": 1300.00,
                                  "effectiveAt": "2999-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.effectiveAt").exists());
    }

    @Test
    void returnsNotFoundBeforeTheOpeningSnapshotOrForAnUnknownAccount() throws Exception {
        UUID accountId = createAccount();

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .queryParam("asOf", "2026-08-19T23:59:59Z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsSnapshotsForArchivedAccountsAndExposesNoDeletionEndpoint() throws Exception {
        UUID accountId = createAccount();
        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{accountId}/balance-snapshots", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": 1300.00,
                                  "effectiveAt": "2026-08-21T12:00:00Z"
                                }
                                """))
                .andExpect(status().isConflict());

        UUID openingSnapshotId = snapshotRepository.findAll().getFirst().getId();
        mockMvc.perform(delete("/api/v1/accounts/{accountId}/balance-snapshots/{snapshotId}",
                        accountId, openingSnapshotId))
                .andExpect(status().isMethodNotAllowed());
    }

    private UUID createAccount() throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Everyday Checking",
                                  "type": "checking",
                                  "currency": "USD",
                                  "openingDate": "2026-08-20",
                                  "openingBalance": 1250.75
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createSnapshot(UUID accountId, String balance, String effectiveAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts/{accountId}/balance-snapshots", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": %s,
                                  "effectiveAt": "%s"
                                }
                                """.formatted(balance, effectiveAt)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/balance-snapshots/")))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
