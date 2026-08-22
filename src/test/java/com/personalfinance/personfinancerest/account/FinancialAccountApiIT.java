package com.personalfinance.personfinancerest.account;

import com.personalfinance.personfinancerest.user.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialAccountApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialAccountRepository repository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void clearAccounts() {
        repository.deleteAll();
    }

    @Test
    void createsFinancialAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Everyday Checking",
                                  "type": "checking",
                                  "currency": "usd",
                                  "openingDate": "2026-08-20",
                                  "openingBalance": 1250.75
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/accounts/.+")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value(currentUserProvider.userId().toString()))
                .andExpect(jsonPath("$.name").value("Everyday Checking"))
                .andExpect(jsonPath("$.type").value("checking"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.openingDate").value("2026-08-20"))
                .andExpect(jsonPath("$.openingBalance").value(1250.75))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        FinancialAccount saved = repository.findAll().getFirst();
        assertThat(saved.getOpeningBalance()).isEqualByComparingTo(new BigDecimal("1250.75"));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void defaultsOpeningBalanceToZero() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wallet",
                                  "type": "cash",
                                  "currency": "USD",
                                  "openingDate": "2026-08-20"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingBalance").value(0.0));

        assertThat(repository.findAll().getFirst().getOpeningBalance())
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void rejectsMissingRequiredFieldsAndExcessDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "type": null,
                                  "currency": "US",
                                  "openingBalance": 1.234
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.currency").exists())
                .andExpect(jsonPath("$.fieldErrors.openingDate").exists())
                .andExpect(jsonPath("$.fieldErrors.openingBalance").exists());

        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsUnsupportedAccountType() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Brokerage",
                                  "type": "brokerage",
                                  "currency": "USD",
                                  "openingDate": "2026-08-20"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request body is malformed"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void retrievesCreatedAccounts() throws Exception {
        createAccount("Everyday Checking", "checking");
        createAccount("Emergency Savings", "savings");

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Everyday Checking"))
                .andExpect(jsonPath("$[1].name").value("Emergency Savings"));

        UUID accountId = repository.findAll().getFirst().getId();

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.ownerId").value(currentUserProvider.userId().toString()));
    }

    @Test
    void returnsStableNotFoundErrorForUnknownAccount() throws Exception {
        UUID unknownAccountId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/accounts/{accountId}", unknownAccountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Financial account not found: " + unknownAccountId))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void allowsCorsPreflightFromConfiguredAngularOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/accounts")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")));
    }

    private void createAccount(String name, String type) throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "type": "%s",
                                  "currency": "USD",
                                  "openingDate": "2026-08-20"
                                }
                                """.formatted(name, type)))
                .andExpect(status().isCreated());
    }
}
