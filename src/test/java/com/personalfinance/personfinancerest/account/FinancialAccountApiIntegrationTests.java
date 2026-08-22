package com.personalfinance.personfinancerest.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialAccountApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialAccountRepository repository;

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
                .andExpect(jsonPath("$.ownerId").value(FinancialAccountService.DEFAULT_OWNER_ID.toString()))
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
}
