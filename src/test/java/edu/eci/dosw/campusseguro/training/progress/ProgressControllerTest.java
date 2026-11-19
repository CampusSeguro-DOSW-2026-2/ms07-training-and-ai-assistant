package edu.eci.dosw.campusseguro.training.progress;

import com.jayway.jsonpath.JsonPath;
import edu.eci.dosw.campusseguro.training.audit.AuditEvent;
import edu.eci.dosw.campusseguro.training.audit.AuditEventRepository;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test uses its own user, so it only sees the progress it creates.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgressControllerTest {

    private static final String PROGRESS = "/api/v1/training/progress/me";
    private static final String EVALUATIONS = "/api/v1/training/evaluations";
    private static final String CONTENTS = "/api/v1/training/contents";
    private static final String CATEGORIES = "/api/v1/training/categories";
    private static final String ADMIN = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilter;

    @Autowired
    private ContentViewRepository views;

    @Autowired
    private ContentRepository contents;

    @Autowired
    private AuditEventRepository auditEvents;

    private MockMvc mvc;
    private String user;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        user = UUID.randomUUID().toString();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", ADMIN).header("X-User-Role", "ADMIN");
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", user).header("X-User-Role", "ESTUDIANTE");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String createCategory() throws Exception {
        String body = mvc.perform(asAdmin(json(post(CATEGORIES), "{\"name\":\"Test " + UUID.randomUUID() + "\"}")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createContent(String categoryId, String title) throws Exception {
        String body = mvc.perform(asAdmin(json(post(CONTENTS),
                        "{\"title\":\"" + title + "\",\"body\":\"Texto.\",\"categoryId\":\"" + categoryId + "\"}")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String question(String statement) {
        return "{\"statement\":\"" + statement + "\",\"options\":[{\"text\":\"Correcta\",\"correct\":true},"
                + "{\"text\":\"Incorrecta\",\"correct\":false}]}";
    }

    private static String evaluationBody(String categoryId, String title, String topic) {
        return "{\"title\":\"" + title + "\",\"topic\":\"" + topic + "\",\"categoryId\":\"" + categoryId
                + "\",\"questions\":[" + question("Uno") + "," + question("Dos") + "," + question("Tres") + "]}";
    }

    private String createEvaluation(String categoryId, String title) throws Exception {
        return mvc.perform(asAdmin(json(post(EVALUATIONS), evaluationBody(categoryId, title, "Rutas de salida"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    // Answers the three questions; "correct" says how many of them are answered right.
    private void answer(String evaluation, int correct) throws Exception {
        StringBuilder sb = new StringBuilder("{\"answers\":[");
        for (int i = 0; i < 3; i++) {
            String questionId = JsonPath.read(evaluation, "$.questions[" + i + "].id");
            String optionId = JsonPath.read(evaluation, "$.questions[" + i + "].options[" + (i < correct ? 0 : 1) + "].id");
            sb.append(i > 0 ? "," : "").append("{\"questionId\":\"").append(questionId)
                    .append("\",\"optionId\":\"").append(optionId).append("\"}");
        }
        String id = JsonPath.read(evaluation, "$.id");
        mvc.perform(asUser(json(post(EVALUATIONS + "/" + id + "/attempts"), sb.append("]}").toString())))
                .andExpect(status().isCreated());
    }

    private void view(String contentId, Instant at) {
        views.save(new ContentView(UUID.fromString(user), contents.findById(UUID.fromString(contentId)).orElseThrow(), at));
    }

    private long events(String type, String userId) {
        return auditEvents.findAll().stream()
                .filter(e -> e.getEventType().equals(type) && e.getUserId().toString().equals(userId))
                .count();
    }

    @Test
    void withoutRecordsBothListsAreEmpty() throws Exception {
        mvc.perform(asUser(get(PROGRESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contents", hasSize(0)))
                .andExpect(jsonPath("$.evaluations", hasSize(0)));
    }

    @Test
    void viewedContentsComeWithTheLastViewNewestFirst() throws Exception {
        String categoryId = createCategory();
        String older = createContent(categoryId, "Antes del sismo");
        String newer = createContent(categoryId, "Después del sismo");
        view(older, Instant.parse("2026-11-10T10:00:00Z"));
        view(newer, Instant.parse("2026-11-12T10:00:00Z"));

        mvc.perform(asUser(get(PROGRESS)))
                .andExpect(jsonPath("$.contents", hasSize(2)))
                .andExpect(jsonPath("$.contents[0].title").value("Después del sismo"))
                .andExpect(jsonPath("$.contents[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.contents[0].lastViewedAt").value("2026-11-12T10:00:00Z"))
                .andExpect(jsonPath("$.contents[0].available").value(true))
                .andExpect(jsonPath("$.contents[1].contentId").value(older));
    }

    @Test
    void aContentDeactivatedLaterIsShownAsNotAvailable() throws Exception {
        String contentId = createContent(createCategory(), "Evacuación");
        view(contentId, Instant.now());
        mvc.perform(asAdmin(patch(CONTENTS + "/" + contentId + "/deactivate"))).andExpect(status().isOk());

        mvc.perform(asUser(get(PROGRESS)))
                .andExpect(jsonPath("$.contents[0].contentId").value(contentId))
                .andExpect(jsonPath("$.contents[0].available").value(false));
    }

    @Test
    void eachEvaluationShowsItsBestAttempt() throws Exception {
        String categoryId = createCategory();
        String evaluation = createEvaluation(categoryId, "Evacuación básica");
        answer(evaluation, 1);
        answer(evaluation, 3);
        answer(evaluation, 2);

        mvc.perform(asUser(get(PROGRESS)))
                .andExpect(jsonPath("$.evaluations", hasSize(1)))
                .andExpect(jsonPath("$.evaluations[0].evaluationId").value((String) JsonPath.read(evaluation, "$.id")))
                .andExpect(jsonPath("$.evaluations[0].title").value("Evacuación básica"))
                .andExpect(jsonPath("$.evaluations[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.evaluations[0].topic").value("Rutas de salida"))
                .andExpect(jsonPath("$.evaluations[0].bestScore").value(100))
                .andExpect(jsonPath("$.evaluations[0].attempts").value(3))
                .andExpect(jsonPath("$.evaluations[0].attemptedAt").exists())
                .andExpect(jsonPath("$.evaluations[0].available").value(true));
    }

    @Test
    void anEvaluationDeactivatedLaterIsShownAsNotAvailable() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Evacuación básica");
        answer(evaluation, 2);
        mvc.perform(asAdmin(patch(EVALUATIONS + "/" + JsonPath.read(evaluation, "$.id") + "/deactivate")))
                .andExpect(status().isOk());

        mvc.perform(asUser(get(PROGRESS)))
                .andExpect(jsonPath("$.evaluations[0].bestScore").value(67))
                .andExpect(jsonPath("$.evaluations[0].available").value(false));
    }

    @Test
    void onlyTheProgressOfTheCurrentUserIsReturned() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Evacuación básica");
        answer(evaluation, 3);
        user = UUID.randomUUID().toString();

        mvc.perform(asUser(get(PROGRESS)))
                .andExpect(jsonPath("$.evaluations", hasSize(0)));
    }

    @Test
    void progressRequiresTheQuizPermission() throws Exception {
        mvc.perform(asAdmin(get(PROGRESS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void creatingUpdatingAndAnsweringAreAudited() throws Exception {
        String categoryId = createCategory();
        long createdBefore = events(AuditEvent.EVALUATION_CREATED, ADMIN);
        long updatedBefore = events(AuditEvent.EVALUATION_UPDATED, ADMIN);

        String evaluation = createEvaluation(categoryId, "Evacuación básica");
        String id = JsonPath.read(evaluation, "$.id");
        mvc.perform(asAdmin(json(put(EVALUATIONS + "/" + id), evaluationBody(categoryId, "Editada", "Rutas"))))
                .andExpect(status().isOk());
        evaluation = mvc.perform(asAdmin(get(EVALUATIONS + "/" + id))).andReturn().getResponse().getContentAsString();
        answer(evaluation, 3);

        assertThat(events(AuditEvent.EVALUATION_CREATED, ADMIN)).isEqualTo(createdBefore + 1);
        assertThat(events(AuditEvent.EVALUATION_UPDATED, ADMIN)).isEqualTo(updatedBefore + 1);
        assertThat(events(AuditEvent.ATTEMPT_REGISTERED, user)).isEqualTo(1);
    }
}
