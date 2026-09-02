package com.personalfinance.personfinancerest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                        .value(org.hamcrest.Matchers.contains(5026.35)))
                .andExpect(jsonPath("$[?(@.name == 'Everyday Checking')].classification")
                        .value(org.hamcrest.Matchers.contains("asset")))
                .andExpect(jsonPath("$[?(@.name == 'Emergency Savings')].interestRate")
                        .value(org.hamcrest.Matchers.contains(4.25)))
                .andExpect(jsonPath("$[?(@.name == 'Previous Credit Card')].classification")
                        .value(org.hamcrest.Matchers.contains("liability")))
                .andExpect(jsonPath("$[?(@.name == 'Previous Credit Card')].interestRateType")
                        .value(org.hamcrest.Matchers.contains("apr")));

        mockMvc.perform(get("/api/v1/categories").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));

        mockMvc.perform(get("/api/v1/recurring-expenses").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.name == 'Home internet')].intervalMonths")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.name == 'Auto insurance')].intervalMonths")
                        .value(org.hamcrest.Matchers.contains(6)))
                .andExpect(jsonPath("$[?(@.name == 'Annual software subscription')].intervalMonths")
                        .value(org.hamcrest.Matchers.contains(12)))
                .andExpect(jsonPath("$[?(@.name == 'Former gym membership')].status")
                        .value(org.hamcrest.Matchers.contains("archived")));

        mockMvc.perform(get("/api/v1/transactions").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(14))
                .andExpect(jsonPath("$.items[?(@.description == 'Weekly groceries')].splits[0].amount")
                        .value(org.hamcrest.Matchers.contains(100.0)))
                .andExpect(jsonPath("$.totalElements").value(14))
                .andExpect(jsonPath("$.items[?(@.description == 'August home internet')]"
                                + ".recurringExpenseOccurrence.occurrenceKey")
                        .value(org.hamcrest.Matchers.contains(
                                "80000000-0000-0000-0000-000000000001:2026-08-31")));

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
                .andExpect(jsonPath("$[1].spending").value(285.64))
                .andExpect(jsonPath("$[1].netImpact").value(2932.61))
                .andExpect(jsonPath("$[1].transactionCount").value(8));

        mockMvc.perform(get("/api/v1/budgets").queryParam("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("August Spending Plan"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].totalPlanned").value(800.0))
                .andExpect(jsonPath("$[0].lines.length()").value(3))
                .andExpect(jsonPath("$[0].lines[2].status").value("archived"));

        mockMvc.perform(get("/api/v1/budgets/70000000-0000-0000-0000-000000000001/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planned").value(800.0))
                .andExpect(jsonPath("$.committed").value(929.99))
                .andExpect(jsonPath("$.outstandingScheduledTarget").value(840.0))
                .andExpect(jsonPath("$.totalBudgeted").value(1729.99))
                .andExpect(jsonPath("$.unbudgetedCommitments.length()").value(2))
                .andExpect(jsonPath("$.flexibleActual").value(153.65))
                .andExpect(jsonPath("$.billActual").value(84.99))
                .andExpect(jsonPath("$.budgetedActual").value(238.64))
                .andExpect(jsonPath("$.unbudgetedActual").value(47.0))
                .andExpect(jsonPath("$.totalActual").value(285.64))
                .andExpect(jsonPath("$.projectedUsage").value(1125.64))
                .andExpect(jsonPath("$.components.length()").value(5))
                .andExpect(jsonPath("$.components[?(@.name == 'Home internet')].status")
                        .value(org.hamcrest.Matchers.contains("satisfied")))
                .andExpect(jsonPath("$.components[?(@.name == 'Home internet')].actual")
                        .value(org.hamcrest.Matchers.contains(84.99)))
                .andExpect(jsonPath("$.unbudgeted[?(@.categoryId == "
                                + "'30000000-0000-0000-0000-000000000008')].actual")
                        .value(org.hamcrest.Matchers.contains(12.0)));

        mockMvc.perform(get("/api/v1/budgets/70000000-0000-0000-0000-000000000001/progress/transactions")
                        .queryParam("scope", "overall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItems(
                        "40000000-0000-0000-0000-000000000002",
                        "40000000-0000-0000-0000-000000000003",
                        "40000000-0000-0000-0000-000000000011",
                        "40000000-0000-0000-0000-000000000012",
                        "40000000-0000-0000-0000-000000000013",
                        "40000000-0000-0000-0000-000000000014"
                )));
    }

    @Test
    @Transactional
    void suppliesAnArchivedCopySourceAndAnOccupiedTargetMonth() throws Exception {
        String source = "/api/v1/budgets/70000000-0000-0000-0000-000000000002";
        mockMvc.perform(get(source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"))
                .andExpect(jsonPath("$.totalPlanned").value(700.75));
        mockMvc.perform(post(source + "/copy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-09\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-30"))
                .andExpect(jsonPath("$.totalPlanned").value(700.75))
                .andExpect(jsonPath("$.lines.length()").value(2));
        mockMvc.perform(post(source + "/copy").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetMonth":"2026-10","lines":[
                                  {"categoryId":"30000000-0000-0000-0000-000000000004","plannedAmount":200.00},
                                  {"categoryId":"30000000-0000-0000-0000-000000000001","plannedAmount":550.00}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPlanned").value(750.0))
                .andExpect(jsonPath("$.lines[0].categoryId")
                        .value("30000000-0000-0000-0000-000000000004"))
                .andExpect(jsonPath("$.lines[0].position").value(0))
                .andExpect(jsonPath("$.lines[1].categoryId")
                        .value("30000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.lines[1].position").value(1));
        mockMvc.perform(post(source + "/copy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-08\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingBudgetId").value("70000000-0000-0000-0000-000000000001"));
        mockMvc.perform(get(source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"))
                .andExpect(jsonPath("$.version").value(4));
    }
}
