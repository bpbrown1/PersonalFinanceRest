package com.personalfinance.personfinancerest.category;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionCategoryApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionCategoryRepository repository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCategories() {
        repository.deleteAll();
        jdbcTemplate.update("DELETE FROM app_user WHERE id <> ?", currentUserProvider.userId());
    }

    @Test
    void createsAndRetrievesACategory() throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Food   and Dining  ","applicability":"expense"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/categories/.+")))
                .andExpect(jsonPath("$.ownerId").value(currentUserProvider.userId().toString()))
                .andExpect(jsonPath("$.name").value("Food and Dining"))
                .andExpect(jsonPath("$.applicability").value("expense"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.archivedAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID categoryId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.toString()));

        assertThat(repository.findById(categoryId)).isPresent();
    }

    @Test
    void validatesCreateAndUpdateRequests() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"applicability\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.applicability").exists());

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Groceries\",\"applicability\":\"transfer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request body is malformed"));

        UUID categoryId = createCategory("Groceries", "expense");
        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.anyFieldPresent").exists());
    }

    @Test
    void updatesCategoryNameAndApplicability() throws Exception {
        UUID categoryId = createCategory("Pay", "income");

        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Salary\",\"applicability\":\"both\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Salary"))
                .andExpect(jsonPath("$.applicability").value("both"));
    }

    @Test
    void listsAlphabeticallyAndFiltersLifecycleState() throws Exception {
        UUID utilitiesId = createCategory("Utilities", "expense");
        createCategory("Dining", "expense");
        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", utilitiesId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Dining"));
        mockMvc.perform(get("/api/v1/categories?status=archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Utilities"));
        mockMvc.perform(get("/api/v1/categories?status=all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Dining"));
        mockMvc.perform(get("/api/v1/categories?status=deleted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void enforcesCaseAndWhitespaceInsensitiveActiveNameUniqueness() throws Exception {
        createCategory("Food and Dining", "expense");

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" FOOD   AND DINING \",\"applicability\":\"both\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void permitsAnArchivedNameToBeReusedButRejectsItsRestore() throws Exception {
        UUID archivedId = createCategory("Groceries", "expense");
        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", archivedId))
                .andExpect(status().isOk());

        createCategory(" groceries ", "both");

        mockMvc.perform(post("/api/v1/categories/{categoryId}/restore", archivedId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "An active transaction category already uses the name: Groceries"));
    }

    @Test
    void archiveAndRestoreAreIdempotent() throws Exception {
        UUID categoryId = createCategory("Groceries", "expense");

        String archivedAt = objectMapper.readTree(mockMvc.perform(
                                post("/api/v1/categories/{categoryId}/archive", categoryId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("archived"))
                        .andReturn().getResponse().getContentAsString())
                .get("archivedAt").asText();
        mockMvc.perform(post("/api/v1/categories/{categoryId}/archive", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").value(archivedAt));
        mockMvc.perform(post("/api/v1/categories/{categoryId}/restore", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(post("/api/v1/categories/{categoryId}/restore", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void hidesCategoriesOwnedByAnotherUser() throws Exception {
        UUID otherOwnerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, display_name) VALUES (?, ?)", otherOwnerId, "Other User");
        TransactionCategory otherCategory = repository.saveAndFlush(new TransactionCategory(
                UUID.randomUUID(), otherOwnerId, "Private", CategoryApplicability.EXPENSE
        ));

        mockMvc.perform(get("/api/v1/categories/{categoryId}", otherCategory.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void doesNotExposePermanentDelete() throws Exception {
        UUID categoryId = createCategory("Groceries", "expense");

        mockMvc.perform(delete("/api/v1/categories/{categoryId}", categoryId))
                .andExpect(status().isMethodNotAllowed());
        assertThat(repository.findById(categoryId)).isPresent();
    }

    private UUID createCategory(String name, String applicability) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayload(name, applicability))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private record CreatePayload(String name, String applicability) {
    }
}
