package edu.eci.dosw.campusseguro.training.evaluation.draft;

import com.jayway.jsonpath.JsonPath;
import edu.eci.dosw.campusseguro.training.audit.AuditEvent;
import edu.eci.dosw.campusseguro.training.audit.AuditEventRepository;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.evaluation.EvaluationRepository;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiPrompt;
import edu.eci.dosw.campusseguro.training.tutor.ai.TutorProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST and GET /api/v1/training/evaluation-drafts with a fake model. The draft is written in the background,
// so the tests poll GET like the front does. Not @Transactional: the background thread must see the data.
@SpringBootTest
@ActiveProfiles("test")
class EvaluationDraftControllerTest {

    private static final String URL = "/api/v1/training/evaluation-drafts";

    static class FakeAi implements AiAdapter {
        volatile String reply;
        volatile RuntimeException failure;
        volatile AiPrompt lastPrompt;
        volatile int calls;

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
        FakeAi draftFakeAi() {
            return new FakeAi();
        }

        @Bean
        @Primary
        AiAdapters draftFakeAdapters(FakeAi fakeAi) {
            return new AiAdapters(List.of(fakeAi), new TutorProperties("fake", null, null, null, null, null, null));
        }
    }

    private static final String VALID = """
            {"title":"Derrames en el laboratorio","topic":"Derrames","questions":[
              {"statement":"¿A quién se avisa primero ante un derrame?","options":[
                {"text":"Al responsable del laboratorio","correct":true},
                {"text":"A un compañero","correct":false},
                {"text":"A nadie","correct":false}]},
              {"statement":"¿Se debe limpiar un derrame sin entrenamiento?","options":[
                {"text":"Sí","correct":false},{"text":"No","correct":true},{"text":"Solo si es poco","correct":false}]},
              {"statement":"¿Pregunta inválida?","options":[{"text":"A","correct":true},{"text":"B","correct":true}]}
            ]}""";

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
    private EvaluationRepository evaluations;

    @Autowired
    private AuditEventRepository auditEvents;

    private MockMvc mvc;
    private Category category;
    private final List<Object> created = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        ai.reply = VALID;
        ai.failure = null;
        ai.lastPrompt = null;
        ai.calls = 0;
        category = categories.save(new Category("Test Borradores " + UUID.randomUUID(), null));
        created.add(contents.save(new Content("Qué hacer ante un derrame o una fuga de sustancias",
                "Si se derrama o se fuga una sustancia, avisa de inmediato al responsable del laboratorio. "
                        + "No intentes limpiarla si no tienes el entrenamiento.", category)));
    }

    // Not transactional, so everything the test created is removed by hand.
    @AfterEach
    void cleanUp() {
        evaluations.deleteAll(evaluations.findByCategoryId(category.getId(), Pageable.unpaged()).getContent());
        created.forEach(entity -> contents.delete((Content) entity));
        created.clear();
        categories.delete(category);
        auditEvents.deleteAll();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request, String user) {
        return request.header("X-User-Id", user)
                .header("X-User-Role", "ADMIN")
                .header("X-User-Permission", "ms07:content:manage");
    }

    private ResultActions start(String user, String body) throws Exception {
        return mvc.perform(asAdmin(post(URL).contentType(MediaType.APPLICATION_JSON).content(body), user));
    }

    private String startDraft(String user, int questions) throws Exception {
        String body = start(user, "{\"categoryId\":\"" + category.getId() + "\",\"questions\":" + questions + "}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EN_PROCESO"))
                .andExpect(jsonPath("$.draft").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }

    // Polls GET like the front, until the draft leaves EN_PROCESO.
    private ResultActions waitFor(String id, String user) throws Exception {
        long until = System.currentTimeMillis() + 5000;
        while (true) {
            ResultActions result = mvc.perform(asAdmin(get(URL + "/" + id), user)).andExpect(status().isOk());
            String status = JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                    "$.status");
            if (!"EN_PROCESO".equals(status) || System.currentTimeMillis() > until) {
                return result;
            }
            Thread.sleep(20);
        }
    }

    @Test
    void generatesADraftWithTheShapeOfPostEvaluationsAndSavesNothing() throws Exception {
        String user = UUID.randomUUID().toString();
        long before = evaluations.count();

        String id = startDraft(user, 5);

        waitFor(id, user)
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("LISTO"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.draft.title").value("Derrames en el laboratorio"))
                .andExpect(jsonPath("$.draft.topic").value("Derrames"))
                .andExpect(jsonPath("$.draft.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.draft.questions", hasSize(2)))
                .andExpect(jsonPath("$.draft.questions[0].statement").value("¿A quién se avisa primero ante un derrame?"))
                .andExpect(jsonPath("$.draft.questions[0].options", hasSize(3)))
                .andExpect(jsonPath("$.draft.questions[0].options[0].text").value("Al responsable del laboratorio"))
                .andExpect(jsonPath("$.draft.questions[0].options[0].correct").value(true))
                .andExpect(jsonPath("$.draft.questions[1].options[1].correct").value(true));

        assertThat(evaluations.count()).isEqualTo(before);
        assertThat(ai.lastPrompt.json()).isTrue();
        assertThat(ai.lastPrompt.instructions()).contains("Escribe exactamente 5 preguntas")
                .contains("avisa de inmediato al responsable del laboratorio");
    }

    @Test
    void theDraftCanBeSavedWithTheUsualEndpoint() throws Exception {
        String user = UUID.randomUUID().toString();
        String id = startDraft(user, 3);
        String body = waitFor(id, user).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String draft = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("draft").toString();

        mvc.perform(asAdmin(post("/api/v1/training/evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(draft), user))
                .andExpect(status().isCreated());
    }

    @Test
    void questionsDefaultToFive() throws Exception {
        String user = UUID.randomUUID().toString();
        String body = start(user, "{\"categoryId\":\"" + category.getId() + "\"}")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        waitFor(JsonPath.read(body, "$.id"), user);

        assertThat(ai.lastPrompt.instructions()).contains("Escribe exactamente 5 preguntas");
    }

    @Test
    void invalidJsonOfTheModelEndsFailedWithAMessage() throws Exception {
        ai.reply = "Lo siento, no puedo generar eso.";
        String user = UUID.randomUUID().toString();

        waitFor(startDraft(user, 5), user)
                .andExpect(jsonPath("$.status").value("FALLIDO"))
                .andExpect(jsonPath("$.message").value(EvaluationDraftGenerator.NO_VALID_QUESTIONS))
                .andExpect(jsonPath("$.draft").doesNotExist());
    }

    @Test
    void modelDownEndsFailed() throws Exception {
        ai.failure = new DependencyUnavailableException("down");
        String user = UUID.randomUUID().toString();

        waitFor(startDraft(user, 5), user)
                .andExpect(jsonPath("$.status").value("FALLIDO"))
                .andExpect(jsonPath("$.message").value(EvaluationDraftService.AI_UNAVAILABLE));
    }

    @Test
    void unknownCategoryAnswers404() throws Exception {
        start(UUID.randomUUID().toString(), "{\"categoryId\":\"" + UUID.randomUUID() + "\",\"questions\":5}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThat(ai.calls).isZero();
    }

    @Test
    void inactiveCategoryAnswers422() throws Exception {
        category.deactivate();
        categories.save(category);

        start(UUID.randomUUID().toString(), "{\"categoryId\":\"" + category.getId() + "\",\"questions\":5}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        assertThat(ai.calls).isZero();
    }

    @Test
    void categoryWithoutActiveContentsAnswers422() throws Exception {
        Content content = (Content) created.get(0);
        content.deactivate();
        contents.save(content);

        start(UUID.randomUUID().toString(), "{\"categoryId\":\"" + category.getId() + "\",\"questions\":5}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no tiene contenidos activos")));
    }

    @Test
    void questionsOutOfRangeAnswer400() throws Exception {
        for (int questions : new int[]{2, 11}) {
            start(UUID.randomUUID().toString(), "{\"categoryId\":\"" + category.getId() + "\",\"questions\":" + questions + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("questions")));
        }
        start(UUID.randomUUID().toString(), "{\"questions\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("categoryId")));
    }

    @Test
    void anotherUserCannotSeeTheDraft() throws Exception {
        String owner = UUID.randomUUID().toString();
        String id = startDraft(owner, 5);
        waitFor(id, owner);

        mvc.perform(asAdmin(get(URL + "/" + id), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value(EvaluationDraftService.NOT_FOUND));
    }

    @Test
    void anUnknownDraftAnswers404() throws Exception {
        mvc.perform(asAdmin(get(URL + "/" + UUID.randomUUID()), UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theSixthDraftInAnHourAnswers429() throws Exception {
        String user = UUID.randomUUID().toString();
        for (int i = 0; i < EvaluationDraftService.DRAFTS_PER_HOUR; i++) {
            waitFor(startDraft(user, 3), user);
        }

        start(user, "{\"categoryId\":\"" + category.getId() + "\",\"questions\":3}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADAS_SOLICITUDES"))
                .andExpect(jsonPath("$.mensaje").value(EvaluationDraftService.TOO_MANY));
        assertThat(ai.calls).isEqualTo(EvaluationDraftService.DRAFTS_PER_HOUR);
    }

    @Test
    void eachDraftIsAuditedWithoutItsContent() throws Exception {
        UUID user = UUID.randomUUID();
        waitFor(startDraft(user.toString(), 5), user.toString());

        List<AuditEvent> events = auditEvents.findAll().stream().filter(e -> e.getUserId().equals(user)).toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("EVALUACION_GENERADA_IA");
        assertThat(events.get(0).getResult()).isNull();
    }

    @Test
    void withoutManagePermissionAnswers403() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + category.getId() + "\"}")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ESTUDIANTE")
                        .header("X-User-Permission", "ms07:content:read,ms07:quiz:take,ms07:tutor:use"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(get(URL + "/" + UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ESTUDIANTE")
                        .header("X-User-Permission", "ms07:quiz:take"))
                .andExpect(status().isForbidden());
        assertThat(ai.calls).isZero();
    }

    @Test
    void withoutIdentityAnswers401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
