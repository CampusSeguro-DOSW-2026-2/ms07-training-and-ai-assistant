package edu.eci.dosw.campusseguro.training.content;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test creates the categories and contents it needs.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContentControllerTest {

    private static final String URL = "/api/v1/training/contents";
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

    private static String contentBody(String title, String categoryId) {
        return "{\"title\":\"" + title + "\",\"body\":\"Agáchate, cúbrete y sujétate.\",\"categoryId\":\"" + categoryId + "\"}";
    }

    private String createCategory(String name) throws Exception {
        String body = mvc.perform(asAdmin(json(post(CATEGORIES), "{\"name\":\"" + name + "\"}")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createContent(String title, String categoryId) throws Exception {
        String body = mvc.perform(asAdmin(json(post(URL), contentBody(title, categoryId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void deactivate(String url) throws Exception {
        mvc.perform(asAdmin(patch(url + "/deactivate"))).andExpect(status().isOk());
    }

    @Test
    void adminCreatesContent() throws Exception {
        String categoryId = createCategory("Test Salones");
        mvc.perform(asAdmin(json(post(URL), contentBody("  Si tiembla en clase ", categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Si tiembla en clase"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createInInactiveCategoryAnswers422() throws Exception {
        String categoryId = createCategory("Test Salones");
        deactivate(CATEGORIES + "/" + categoryId);
        mvc.perform(asAdmin(json(post(URL), contentBody("T", categoryId))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    @Test
    void createInUnknownCategoryAnswers422() throws Exception {
        mvc.perform(asAdmin(json(post(URL), contentBody("T", UUID.randomUUID().toString()))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void missingFieldsAnswer400() throws Exception {
        mvc.perform(asAdmin(json(post(URL), "{\"title\":\" \",\"body\":\"B\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("title")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("categoryId")));
    }

    @Test
    void studentCannotCreate() throws Exception {
        String categoryId = createCategory("Test Salones");
        mvc.perform(asStudent(json(post(URL), contentBody("T", categoryId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentListOnlyShowsVisibleContentsWithoutBody() throws Exception {
        String active = createCategory("Test Activa");
        String inactive = createCategory("Test Inactiva");
        createContent("Visible", active);
        String hiddenId = createContent("Oculto", active);
        createContent("De categoria inactiva", inactive);
        deactivate(URL + "/" + hiddenId);
        deactivate(CATEGORIES + "/" + inactive);

        mvc.perform(asStudent(get(URL).param("includeInactive", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].title", hasItem("Visible")))
                .andExpect(jsonPath("$.contenido[*].title", not(hasItem("Oculto"))))
                .andExpect(jsonPath("$.contenido[*].title", not(hasItem("De categoria inactiva"))))
                .andExpect(jsonPath("$.contenido[0].body").doesNotExist());

        mvc.perform(asAdmin(get(URL).param("includeInactive", "true")))
                .andExpect(jsonPath("$.contenido[*].title", hasItem("Oculto")))
                .andExpect(jsonPath("$.contenido[*].title", hasItem("De categoria inactiva")));
    }

    @Test
    void listFiltersByCategory() throws Exception {
        String first = createCategory("Test Uno");
        String second = createCategory("Test Dos");
        createContent("Del uno", first);
        createContent("Del dos", second);

        mvc.perform(asStudent(get(URL).param("categoryId", first)))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.contenido[0].title").value("Del uno"));
    }

    @Test
    void hiddenContentAnswers404ToStudentsButNotToAdmin() throws Exception {
        String categoryId = createCategory("Test Salones");
        String id = createContent("Oculto", categoryId);
        mvc.perform(asStudent(get(URL + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Agáchate, cúbrete y sujétate."));

        deactivate(URL + "/" + id);
        mvc.perform(asStudent(get(URL + "/" + id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(asAdmin(get(URL + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVO"));
    }

    @Test
    void updateCanKeepAnInactiveCategoryButNotMoveToOne() throws Exception {
        String current = createCategory("Test Actual");
        String other = createCategory("Test Otra");
        String id = createContent("T", current);
        deactivate(CATEGORIES + "/" + current);
        deactivate(CATEGORIES + "/" + other);

        mvc.perform(asAdmin(json(put(URL + "/" + id), contentBody("Nuevo titulo", current))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nuevo titulo"));
        mvc.perform(asAdmin(json(put(URL + "/" + id), contentBody("Nuevo titulo", other))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void deactivateAndReactivateRules() throws Exception {
        String categoryId = createCategory("Test Salones");
        String id = createContent("T", categoryId);
        mvc.perform(asAdmin(patch(URL + "/" + id + "/reactivate"))).andExpect(status().isConflict());
        deactivate(URL + "/" + id);
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate"))).andExpect(status().isConflict());
        mvc.perform(asAdmin(patch(URL + "/" + id + "/reactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void unknownContentAnswers404() throws Exception {
        mvc.perform(asAdmin(get(URL + "/" + UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
