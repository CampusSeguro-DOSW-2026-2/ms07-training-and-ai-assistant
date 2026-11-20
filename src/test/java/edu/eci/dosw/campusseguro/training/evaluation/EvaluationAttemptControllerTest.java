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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Listing, reading and answering evaluations. Each test creates its own category and evaluation.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvaluationAttemptControllerTest {

    private static final String URL = "/api/v1/training/evaluations";
    private static final String CATEGORIES = "/api/v1/training/categories";

    // Three questions; the correct option is always the first one.
    private static final String THREE_QUESTIONS = "[" + question("¿Por dónde se sale?") + ","
            + question("¿Qué se lleva?") + "," + question("¿Dónde se espera?") + "]";

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
        return request.header("X-User-Id", "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42").header("X-User-Role", "ADMIN").header("X-User-Permission", "ms07:content:manage");
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19").header("X-User-Role", "ESTUDIANTE").header("X-User-Permission", "ms07:content:read,ms07:quiz:take,ms07:tutor:use");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String question(String statement) {
        return "{\"statement\":\"" + statement + "\",\"options\":[{\"text\":\"Correcta\",\"correct\":true},"
                + "{\"text\":\"Incorrecta\",\"correct\":false}]}";
    }

    private String createCategory() throws Exception {
        String body = mvc.perform(asAdmin(json(post(CATEGORIES), "{\"name\":\"Test " + UUID.randomUUID() + "\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // Returns the created evaluation as JSON, with its question and option ids.
    private String createEvaluation(String categoryId, String title) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"topic\":\"Rutas de salida\",\"categoryId\":\"" + categoryId
                + "\",\"questions\":" + THREE_QUESTIONS + "}";
        return mvc.perform(asAdmin(json(post(URL), body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    // Answers question i with option i of the choices (0 = correct, 1 = incorrect).
    private static String answers(String evaluation, int... choices) {
        StringBuilder sb = new StringBuilder("{\"answers\":[");
        for (int i = 0; i < choices.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String questionId = JsonPath.read(evaluation, "$.questions[" + i + "].id");
            String optionId = JsonPath.read(evaluation, "$.questions[" + i + "].options[" + choices[i] + "].id");
            sb.append("{\"questionId\":\"").append(questionId).append("\",\"optionId\":\"").append(optionId).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String id(String evaluation) {
        return JsonPath.read(evaluation, "$.id");
    }

    @Test
    void communityListsOnlyActiveEvaluationsEvenIfTheirCategoryIsInactive() throws Exception {
        String categoryId = createCategory();
        createEvaluation(categoryId, "Test Visible");
        String hidden = createEvaluation(categoryId, "Test Oculta");
        mvc.perform(asAdmin(patch(URL + "/" + id(hidden) + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asAdmin(patch(CATEGORIES + "/" + categoryId + "/deactivate"))).andExpect(status().isOk());

        mvc.perform(asStudent(get(URL).param("categoryId", categoryId).param("includeInactive", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].title", hasItem("Test Visible")))
                .andExpect(jsonPath("$.contenido[*].title", not(hasItem("Test Oculta"))))
                .andExpect(jsonPath("$.total_elementos").value(1));
        mvc.perform(asAdmin(get(URL).param("categoryId", categoryId).param("includeInactive", "true")))
                .andExpect(jsonPath("$.total_elementos").value(2));
    }

    @Test
    void communityDoesNotReceiveTheCorrectOption() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asStudent(get(URL + "/" + id(evaluation))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(3)))
                .andExpect(jsonPath("$.questions[0].options[0].text").value("Correcta"))
                .andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist());
        mvc.perform(asAdmin(get(URL + "/" + id(evaluation))))
                .andExpect(jsonPath("$.questions[0].options[0].correct").value(true));
    }

    @Test
    void inactiveEvaluationIsNotFoundForTheCommunity() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asAdmin(patch(URL + "/" + id(evaluation) + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asStudent(get(URL + "/" + id(evaluation))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void allCorrectAnswersScore100() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 0, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.correctAnswers").value(3))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.topic").value("Rutas de salida"))
                .andExpect(jsonPath("$.attemptedAt").exists());
    }

    @Test
    void scoreIsThePercentOfCorrectAnswersAndShowsTheCorrection() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        String correctOfSecond = JsonPath.read(evaluation, "$.questions[1].options[0].id");
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 1, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(33))
                .andExpect(jsonPath("$.correctAnswers").value(1))
                .andExpect(jsonPath("$.answers[0].correct").value(true))
                .andExpect(jsonPath("$.answers[1].correct").value(false))
                .andExpect(jsonPath("$.answers[1].correctOptionId").value(correctOfSecond));
    }

    @Test
    void aUserCanAnswerSeveralTimes() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        String url = URL + "/" + id(evaluation) + "/attempts";
        mvc.perform(asStudent(json(post(url), answers(evaluation, 1, 1, 1))))
                .andExpect(jsonPath("$.score").value(0));
        mvc.perform(asStudent(json(post(url), answers(evaluation, 0, 0, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(67));
    }

    @Test
    void unansweredQuestionAnswers400() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("answers"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Falta responder la pregunta 3."));
    }

    @Test
    void optionOfAnotherQuestionAnswers400() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        String optionOfSecond = JsonPath.read(evaluation, "$.questions[1].options[0].id");
        String body = answers(evaluation, 0, 0, 0)
                .replace(JsonPath.<String>read(evaluation, "$.questions[0].options[0].id"), optionOfSecond);
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(containsString("no pertenece a la pregunta 1")));
    }

    @Test
    void sameQuestionTwiceAnswers400() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        String questionId = JsonPath.read(evaluation, "$.questions[0].id");
        String optionId = JsonPath.read(evaluation, "$.questions[0].options[0].id");
        String answer = "{\"questionId\":\"" + questionId + "\",\"optionId\":\"" + optionId + "\"}";
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"),
                        "{\"answers\":[" + answer + "," + answer + "]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Cada pregunta se responde una sola vez."));
    }

    @Test
    void questionOfAnotherEvaluationAnswers400() throws Exception {
        String categoryId = createCategory();
        String evaluation = createEvaluation(categoryId, "Test Evacuación");
        String other = createEvaluation(categoryId, "Test Otra");
        String extra = answers(other, 0).replace("{\"answers\":[", ",").replace("]}", "");
        String body = answers(evaluation, 0, 0, 0).replace("]}", extra + "]}");
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Hay respuestas a preguntas que no son de esta evaluación."));
    }

    @Test
    void missingAnswersAnswers400() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("answers"));
    }

    @Test
    void inactiveEvaluationAnswers422AndRegistersNothing() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asAdmin(patch(URL + "/" + id(evaluation) + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 0, 0))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));

        // With no attempt registered the evaluation can still be edited.
        String categoryId = JsonPath.read(evaluation, "$.categoryId");
        String update = "{\"title\":\"Test Editada\",\"topic\":\"Rutas\",\"categoryId\":\"" + categoryId
                + "\",\"questions\":[" + question("¿Por dónde se sale?") + "]}";
        mvc.perform(asAdmin(json(put(URL + "/" + id(evaluation)), update))).andExpect(status().isOk());
    }

    @Test
    void evaluationWithAttemptsCannotBeEdited() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 0, 0))))
                .andExpect(status().isCreated());
        String categoryId = JsonPath.read(evaluation, "$.categoryId");
        String update = "{\"title\":\"Test Editada\",\"topic\":\"Rutas\",\"categoryId\":\"" + categoryId
                + "\",\"questions\":[" + question("¿Por dónde se sale?") + "]}";
        mvc.perform(asAdmin(json(put(URL + "/" + id(evaluation)), update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void adminCannotAnswerEvaluations() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test Evacuación");
        mvc.perform(asAdmin(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 0, 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownEvaluationAnswers404() throws Exception {
        mvc.perform(asStudent(get(URL + "/" + UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(asStudent(json(post(URL + "/" + UUID.randomUUID() + "/attempts"), "{\"answers\":[]}")))
                .andExpect(status().isNotFound());
    }

    // Outside a test transaction, like a real request (open-in-view is off).
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void readingListingAndAnsweringWorkOutsideATransaction() throws Exception {
        String evaluation = createEvaluation(createCategory(), "Test " + UUID.randomUUID());
        mvc.perform(asStudent(get(URL))).andExpect(status().isOk());
        mvc.perform(asStudent(get(URL + "/" + id(evaluation))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[2].options", hasSize(2)));
        mvc.perform(asStudent(json(post(URL + "/" + id(evaluation) + "/attempts"), answers(evaluation, 0, 0, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(100));
    }
}
