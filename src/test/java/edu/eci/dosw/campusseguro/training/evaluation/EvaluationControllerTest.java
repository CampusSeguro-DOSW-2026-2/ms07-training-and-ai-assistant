package edu.eci.dosw.campusseguro.training.evaluation;

import com.jayway.jsonpath.JsonPath;
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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test creates the categories it needs, so it does not depend on the seed migration.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvaluationControllerTest {

    private static final String URL = "/api/v1/training/evaluations";
    private static final String CATEGORIES = "/api/v1/training/categories";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42").header("X-User-Role", "ADMIN");
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19").header("X-User-Role", "ESTUDIANTE");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String createCategory(String name) throws Exception {
        String body = mvc.perform(asAdmin(json(post(CATEGORIES), "{\"name\":\"" + name + "\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void deactivateCategory(String id) throws Exception {
        mvc.perform(asAdmin(patch(CATEGORIES + "/" + id + "/deactivate"))).andExpect(status().isOk());
    }

    private static String evaluation(String categoryId, String questions) {
        return "{\"title\":\" Evacuación básica \",\"topic\":\" Rutas de salida \",\"categoryId\":\"" + categoryId
                + "\",\"questions\":" + questions + "}";
    }

    private static final String ONE_QUESTION = "[{\"statement\":\" ¿Por dónde se sale? \",\"options\":["
            + "{\"text\":\"Por la escalera\",\"correct\":true},{\"text\":\"Por el ascensor\",\"correct\":false}]}]";

    private String createEvaluation(String categoryId) throws Exception {
        String body = mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, ONE_QUESTION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void adminCreatesEvaluationWithTrimmedTextsAndOrderedQuestions() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        String questions = "[" + ONE_QUESTION.substring(1, ONE_QUESTION.length() - 1)
                + ",{\"statement\":\"¿Qué se lleva?\",\"options\":[{\"text\":\"Nada\",\"correct\":true},"
                + "{\"text\":\"El portátil\",\"correct\":false},{\"text\":\"La maleta\",\"correct\":false}]}]";

        mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, questions))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Evacuación básica"))
                .andExpect(jsonPath("$.topic").value("Rutas de salida"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.questions", hasSize(2)))
                .andExpect(jsonPath("$.questions[0].position").value(1))
                .andExpect(jsonPath("$.questions[0].statement").value("¿Por dónde se sale?"))
                .andExpect(jsonPath("$.questions[0].options[0].correct").value(true))
                .andExpect(jsonPath("$.questions[1].position").value(2))
                .andExpect(jsonPath("$.questions[1].options", hasSize(3)))
                .andExpect(jsonPath("$.questions[1].options[2].position").value(3));
    }

    @Test
    void evaluationWithoutQuestionsAnswers422() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, "[]"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.mensaje").value("La evaluación debe tener al menos una pregunta."));
    }

    @Test
    void questionWithOneOptionAnswers422() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        String questions = "[{\"statement\":\"¿Por dónde se sale?\",\"options\":[{\"text\":\"Escalera\",\"correct\":true}]}]";
        mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, questions))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("al menos dos opciones")));
    }

    @Test
    void questionWithoutCorrectOptionAnswers422() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        String questions = "[{\"statement\":\"¿Por dónde se sale?\",\"options\":["
                + "{\"text\":\"Escalera\",\"correct\":false},{\"text\":\"Ascensor\",\"correct\":false}]}]";
        mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, questions))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("exactamente una opción correcta")));
    }

    @Test
    void questionWithTwoCorrectOptionsAnswers422() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        String questions = "[{\"statement\":\"¿Por dónde se sale?\",\"options\":["
                + "{\"text\":\"Escalera\",\"correct\":true},{\"text\":\"Rampa\",\"correct\":true}]}]";
        mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, questions))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("exactamente una opción correcta")));
    }

    @Test
    void blankFieldsAnswer400WithEachField() throws Exception {
        String body = "{\"title\":\" \",\"topic\":\"\",\"questions\":[{\"statement\":\"\",\"options\":["
                + "{\"text\":\"\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}]}";
        mvc.perform(asAdmin(json(post(URL), body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'title')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo == 'topic')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo == 'categoryId')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo == 'questions[0].statement')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo == 'questions[0].options[0].text')]").exists());
    }

    @Test
    void topicLongerThan150Answers400() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        String body = evaluation(categoryId, ONE_QUESTION).replace(" Rutas de salida ", "x".repeat(151));
        mvc.perform(asAdmin(json(post(URL), body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("topic"));
    }

    @Test
    void inactiveCategoryAnswers422OnCreate() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        deactivateCategory(categoryId);
        mvc.perform(asAdmin(json(post(URL), evaluation(categoryId, ONE_QUESTION))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("está inactiva")));
    }

    @Test
    void unknownCategoryAnswers422() throws Exception {
        mvc.perform(asAdmin(json(post(URL), evaluation(UUID.randomUUID().toString(), ONE_QUESTION))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("La categoría no existe."));
    }

    @Test
    void updateReplacesQuestionsAndKeepsItsInactiveCategory() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        String id = createEvaluation(categoryId);
        deactivateCategory(categoryId);
        String questions = "[{\"statement\":\"¿Dónde es el punto de encuentro?\",\"options\":["
                + "{\"text\":\"Plazoleta\",\"correct\":false},{\"text\":\"Cancha\",\"correct\":true}]}]";

        mvc.perform(asAdmin(json(put(URL + "/" + id), evaluation(categoryId, questions))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].statement").value("¿Dónde es el punto de encuentro?"))
                .andExpect(jsonPath("$.questions[0].options[1].correct").value(true));
    }

    @Test
    void updateToAnotherInactiveCategoryAnswers422() throws Exception {
        String id = createEvaluation(createCategory("Test Evacuación"));
        String otherId = createCategory("Test Laboratorios");
        deactivateCategory(otherId);
        mvc.perform(asAdmin(json(put(URL + "/" + id), evaluation(otherId, ONE_QUESTION))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void updateToAnotherActiveCategoryChangesIt() throws Exception {
        String id = createEvaluation(createCategory("Test Evacuación"));
        String otherId = createCategory("Test Laboratorios");
        mvc.perform(asAdmin(json(put(URL + "/" + id), evaluation(otherId, ONE_QUESTION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(otherId));
    }

    @Test
    void updateUnknownEvaluationAnswers404() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        mvc.perform(asAdmin(json(put(URL + "/" + UUID.randomUUID()), evaluation(categoryId, ONE_QUESTION))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void deactivateTwiceAnswers409() throws Exception {
        String id = createEvaluation(createCategory("Test Evacuación"));
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVO"));
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void reactivateWorksOnlyOnInactiveEvaluations() throws Exception {
        String id = createEvaluation(createCategory("Test Evacuación"));
        mvc.perform(asAdmin(patch(URL + "/" + id + "/reactivate")))
                .andExpect(status().isConflict());
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asAdmin(patch(URL + "/" + id + "/reactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void studentCannotManageEvaluations() throws Exception {
        String categoryId = createCategory("Test Evacuación");
        mvc.perform(asStudent(json(post(URL), evaluation(categoryId, ONE_QUESTION))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        String id = createEvaluation(categoryId);
        mvc.perform(asStudent(patch(URL + "/" + id + "/deactivate")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingIdentityHeadersAnswer401() throws Exception {
        mvc.perform(json(post(URL), "{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void invalidIdAnswers400() throws Exception {
        mvc.perform(asAdmin(patch(URL + "/not-a-uuid/deactivate")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
