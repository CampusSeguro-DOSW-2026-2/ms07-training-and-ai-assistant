package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.audit.AuditService;
import edu.eci.dosw.campusseguro.training.audit.TutorQueryResult;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF-15 alternate flow 8a: if the query metadata cannot be saved, the user still gets the answer.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({TutorControllerTest.FakeAiConfig.class, TutorAuditFailureTest.BrokenAuditConfig.class})
class TutorAuditFailureTest {

    // Imported, not nested-detected, so it only applies to this test.
    static class BrokenAuditConfig {

        @Bean
        @Primary
        AuditService brokenAudit() {
            return new AuditService(null) {
                @Override
                public void registerTutorQuery(UUID userId, TutorQueryResult result) {
                    throw new DataAccessResourceFailureException("database down");
                }
            };
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilter;

    @Autowired
    private TutorControllerTest.FakeAi ai;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ContentRepository contents;

    @Test
    void theUserStillGetsTheAnswer() throws Exception {
        ai.reset();
        Category category = categories.save(new Category("Test Auditoría", null));
        contents.save(new Content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.", category));
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();

        mvc.perform(post("/api/v1/tutor/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Qué hago si hay un sismo?\"}")
                        .header("X-User-Id", "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19")
                        .header("X-User-Role", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient").value(true));
    }
}
