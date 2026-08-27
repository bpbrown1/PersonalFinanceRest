package com.personalfinance.personfinancerest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:development-data-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevelopmentDataIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loadsARepresentativeDevelopmentDataset() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.name == 'Everyday Checking')].currentBalance")
                        .value(org.hamcrest.Matchers.contains(5026.35)));

        mockMvc.perform(get("/api/v1/categories").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(12))
                .andExpect(jsonPath("$.items[?(@.description == 'Weekly groceries')].splits[0].amount")
                        .value(org.hamcrest.Matchers.contains(100.0)))
                .andExpect(jsonPath("$.totalElements").value(12));

        mockMvc.perform(get("/api/v1/transactions")
                        .queryParam("type", "transfer_in")
                        .queryParam("sort", "amount")
                        .queryParam("direction", "asc")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(92.0))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.description == 'Travel cash exchange')].sourceAmount")
                        .value(org.hamcrest.Matchers.contains(100.0)))
                .andExpect(jsonPath("$[?(@.description == 'Travel cash exchange')].destinationAmount")
                        .value(org.hamcrest.Matchers.contains(92.0)));

        mockMvc.perform(get("/api/v1/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].income").value(0.0))
                .andExpect(jsonPath("$[0].spending").value(42.75))
                .andExpect(jsonPath("$[0].netImpact").value(-42.75))
                .andExpect(jsonPath("$[0].transactionCount").value(1))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].income").value(3218.25))
                .andExpect(jsonPath("$[1].spending").value(188.65))
                .andExpect(jsonPath("$[1].netImpact").value(3029.60))
                .andExpect(jsonPath("$[1].transactionCount").value(6));

        mockMvc.perform(get("/api/v1/budgets").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("August Spending Plan"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].totalPlanned").value(800.0))
                .andExpect(jsonPath("$[0].lines.length()").value(3))
                .andExpect(jsonPath("$[0].lines[2].status").value("archived"));

        mockMvc.perform(get("/api/v1/budgets/70000000-0000-0000-0000-000000000001/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planned").value(800.0))
                .andExpect(jsonPath("$.budgetedActual").value(153.65))
                .andExpect(jsonPath("$.unbudgetedActual").value(35.0))
                .andExpect(jsonPath("$.totalActual").value(188.65));

        mockMvc.perform(get("/api/v1/budgets/70000000-0000-0000-0000-000000000001/progress/transactions")
                        .queryParam("scope", "overall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItems(
                        "40000000-0000-0000-0000-000000000002",
                        "40000000-0000-0000-0000-000000000003",
                        "40000000-0000-0000-0000-000000000011",
                        "40000000-0000-0000-0000-000000000012"
                )));
    }
}
