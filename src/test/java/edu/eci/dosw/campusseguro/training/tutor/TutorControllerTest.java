package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.audit.AuditEvent;
import edu.eci.dosw.campusseguro.training.audit.AuditEventRepository;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiPrompt;
import edu.eci.dosw.campusseguro.training.tutor.ai.ChatCompletionsAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.TutorProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /api/v1/tutor/questions with a fake model: OpenAI is never called.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TutorControllerTest {

    private static final String URL = "/api/v1/tutor/questions";
    private static final String USER = "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19";

    // Plays the model: answers what the test says and keeps the last prompt.
    static class FakeAi implements AiAdapter {
        String reply;
        RuntimeException failure;
        AiPrompt lastPrompt;
        int calls;

        void reset() {
            reply = "Agáchate, cúbrete y sujétate hasta que pase el movimiento.";
            failure = null;
            lastPrompt = null;
            calls = 0;
        }

        @Override
        public String provider() {
            return "fake";
        }

        @Override
        public String complete(AiPrompt prompt) {
            calls++;
            lastPrompt = prompt;
            if (failure != null) {
                throw failure;
            }
            return reply;
        }
    }

    @TestConfiguration
    static class FakeAiConfig {

        @Bean
        FakeAi fakeAi() {
            return new FakeAi();
        }

        @Bean
        @Primary
        AiAdapters fakeAdapters(FakeAi fakeAi) {
            TutorProperties properties = new TutorProperties("fake", null, null, null, null, null, null);
            return new AiAdapters(List.of(fakeAi), properties);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilter;

    @Autowired
    private FakeAi ai;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ContentRepository contents;

    @Autowired
    private AuditEventRepository auditEvents;

    private MockMvc mvc;
    private Content earthquake;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        ai.reset();
        auditEvents.deleteAll();
        Category category = categories.save(new Category("Test Tutor", null));
        earthquake = contents.save(new Content("Qué hacer durante un sismo",
                "Agáchate, cúbrete y sujétate. Aléjate de ventanas y estanterías.", category));
    }

    private static MockHttpServletRequestBuilder ask(String question, String role) {
        return post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + question + "\"}")
                .header("X-User-Id", USER)
                .header("X-User-Role", role);
    }

    private static MockHttpServletRequestBuilder asStudent(String question) {
        return ask(question, "ESTUDIANTE");
    }

    @Test
    void answersWithTheRelatedContentsAsSources() throws Exception {
        mvc.perform(asStudent("¿Qué hago si hay un sismo en clase?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Agáchate, cúbrete y sujétate hasta que pase el movimiento."))
                .andExpect(jsonPath("$.sufficient").value(true))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].id").value(earthquake.getId().toString()))
                .andExpect(jsonPath("$.sources[0].title").value("Qué hacer durante un sismo"));

        assertThat(ai.lastPrompt.message()).isEqualTo("¿Qué hago si hay un sismo en clase?");
        assertRecorded("RESPONDIDA");
        assertThat(ai.lastPrompt.instructions())
                .contains("ÚNICAMENTE los contenidos")
                .contains(TutorService.NO_INFORMATION_MARK)
                .contains("[1] Qué hacer durante un sismo")
                .contains("Aléjate de ventanas y estanterías.");
    }

    @Test
    void unrelatedQuestionPointsToTheProtocolWithoutCallingTheModel() throws Exception {
        mvc.perform(asStudent("¿Me das una receta de pasta con queso?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient").value(false))
                .andExpect(jsonPath("$.answer").value(TutorService.NO_INFORMATION))
                .andExpect(jsonPath("$.sources", hasSize(0)));

        assertThat(ai.calls).isZero();
        assertRecorded("FUERA_DE_ALCANCE");
    }

    @Test
    void whenTheModelSaysThereIsNoInformationTheTutorPointsToTheProtocol() throws Exception {
        ai.reply = "SIN_INFORMACION";

        mvc.perform(asStudent("¿Cuántos sismos hubo en Bogotá en 1990?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient").value(false))
                .andExpect(jsonPath("$.answer").value(TutorService.NO_INFORMATION))
                .andExpect(jsonPath("$.sources", hasSize(0)));

        assertRecorded("FUERA_DE_ALCANCE");
    }

    @Test
    void aMarkLeftAfterARealAnswerIsRemoved() throws Exception {
        ai.reply = "Agáchate, cúbrete y sujétate. SIN_INFORMACION";

        mvc.perform(asStudent("¿Qué hago si hay un terremoto?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient").value(true))
                .andExpect(jsonPath("$.answer").value("Agáchate, cúbrete y sujétate."))
                .andExpect(jsonPath("$.sources", hasSize(1)));
    }

    @Test
    void theModelIsToldAboutTyposAndSynonyms() throws Exception {
        mvc.perform(asStudent("que hago en un sizmo")).andExpect(status().isOk());

        assertThat(ai.lastPrompt.message()).isEqualTo("que hago en un sizmo");
        assertThat(ai.lastPrompt.instructions()).contains("errores de ortografía").contains("terremoto o temblor");
    }

    @Test
    void inactiveContentIsNotUsed() throws Exception {
        earthquake.deactivate();
        contents.flush();

        mvc.perform(asStudent("¿Qué hago si hay un sismo?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient").value(false));

        assertThat(ai.calls).isZero();
    }

    @Test
    void modelDownAnswers503WithoutGeneratedText() throws Exception {
        ai.failure = new DependencyUnavailableException(ChatCompletionsAdapter.UNAVAILABLE);

        mvc.perform(asStudent("¿Qué hago si hay un sismo?"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value(ChatCompletionsAdapter.UNAVAILABLE));

        assertThat(auditEvents.count()).isZero();
    }

    @Test
    void emptyQuestionAnswers400() throws Exception {
        mvc.perform(asStudent("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("question")));

        assertThat(auditEvents.count()).isZero();
    }

    @Test
    void questionLongerThan500Answers400() throws Exception {
        mvc.perform(asStudent("a".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void missingBodyAnswers400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", USER)
                        .header("X-User-Role", "ESTUDIANTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void withoutTutorPermissionAnswers403() throws Exception {
        mvc.perform(ask("¿Qué hago si hay un sismo?", "AUDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        assertThat(ai.calls).isZero();
    }

    @Test
    void adminWithoutTutorPermissionAnswers403() throws Exception {
        mvc.perform(ask("¿Qué hago si hay un sismo?", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRecordKeepsNoQuestionOrAnswer() throws Exception {
        mvc.perform(asStudent("¿Qué hago si hay un sismo en clase?")).andExpect(status().isOk());

        AuditEvent event = auditEvents.findAll().get(0);
        assertThat(List.of(event.getEventType(), event.getResult(), String.valueOf(event.getCorrelationId())))
                .noneMatch(value -> value.contains("sismo") || value.contains("Agáchate"));
    }

    private void assertRecorded(String result) {
        List<AuditEvent> events = auditEvents.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getUserId()).isEqualTo(UUID.fromString(USER));
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEvent.TUTOR_QUERY);
        assertThat(events.get(0).getResult()).isEqualTo(result);
        assertThat(events.get(0).getOccurredAt()).isNotNull();
    }

    @Test
    void withoutIdentityHeadersAnswers401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"sismo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
