package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.audit.AuditEventRepository;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Usage limit of the tutor with TUTOR_RATE_PER_MINUTE=3 (10 by default).
@SpringBootTest(properties = "campusseguro.tutor.safety.rate-per-minute=3")
@ActiveProfiles("test")
@Transactional
@Import(TutorControllerTest.FakeAiConfig.class)
class TutorRateLimitTest {

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

    @Autowired
    private AuditEventRepository auditEvents;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        ai.reset();
        auditEvents.deleteAll();
        Category category = categories.save(new Category("Test Tutor Límite", null));
        contents.save(new Content("Qué hacer durante un sismo", "Agáchate, cúbrete y sujétate.", category));
    }

    private static MockHttpServletRequestBuilder ask(String user) {
        return post("/api/v1/tutor/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"¿Qué hago si hay un sismo?\"}")
                .header("X-User-Id", user)
                .header("X-User-Role", "ESTUDIANTE")
                .header("X-User-Permission", "ms07:tutor:use");
    }

    @Test
    void overTheLimitAnswers429WithoutCallingTheModelOrRecordingIt() throws Exception {
        String user = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            mvc.perform(ask(user)).andExpect(status().isOk());
        }

        mvc.perform(ask(user))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.codigo").value("DEMASIADAS_SOLICITUDES"))
                .andExpect(jsonPath("$.mensaje").value(TutorRateLimiter.PER_MINUTE_MESSAGE))
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        assertThat(ai.calls).isEqualTo(3);
        assertThat(auditEvents.count()).isEqualTo(3);
    }

    @Test
    void eachUserHasItsOwnLimit() throws Exception {
        String user = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            mvc.perform(ask(user)).andExpect(status().isOk());
        }

        mvc.perform(ask(UUID.randomUUID().toString())).andExpect(status().isOk());
    }
}
