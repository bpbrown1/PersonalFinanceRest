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
                .andExpect(jsonPath("$.length()").value(10));

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
                .andExpect(jsonPath("$[1].spending").value(173.65))
                .andExpect(jsonPath("$[1].netImpact").value(3044.60))
                .andExpect(jsonPath("$[1].transactionCount").value(4));
    }
}
