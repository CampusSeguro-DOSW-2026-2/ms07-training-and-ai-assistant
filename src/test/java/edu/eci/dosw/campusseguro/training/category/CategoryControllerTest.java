package edu.eci.dosw.campusseguro.training.category;

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

// Each test creates the categories it needs, so it does not depend on the seed migration.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryControllerTest {

    private static final String URL = "/api/v1/training/categories";

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
        String body = mvc.perform(asAdmin(json(post(URL), "{\"name\":\"" + name + "\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void listUsesThePageEnvelope() throws Exception {
        createCategory("Test Alpha");
        mvc.perform(asStudent(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].name", hasItem("Test Alpha")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").isNumber())
                .andExpect(jsonPath("$.total_paginas").isNumber());
    }

    @Test
    void inactiveCategoriesAreOnlyVisibleToAdmin() throws Exception {
        createCategory("Test Alpha");
        String betaId = createCategory("Test Beta");
        mvc.perform(asAdmin(patch(URL + "/" + betaId + "/deactivate"))).andExpect(status().isOk());

        mvc.perform(asStudent(get(URL).param("includeInactive", "true")))
                .andExpect(jsonPath("$.contenido[*].name", hasItem("Test Alpha")))
                .andExpect(jsonPath("$.contenido[*].name", not(hasItem("Test Beta"))));
        mvc.perform(asAdmin(get(URL)))
                .andExpect(jsonPath("$.contenido[*].name", not(hasItem("Test Beta"))));
        mvc.perform(asAdmin(get(URL).param("includeInactive", "true")))
                .andExpect(jsonPath("$.contenido[*].name", hasItem("Test Beta")));
    }

    @Test
    void missingIdentityHeadersAnswer401() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void studentCannotCreate() throws Exception {
        mvc.perform(asStudent(json(post(URL), "{\"name\":\"Test Simulacros\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void adminCreatesCategoryWithTrimmedName() throws Exception {
        mvc.perform(asAdmin(json(post(URL), "{\"name\":\"  Test Simulacros \",\"description\":\"Prácticas\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Simulacros"))
                .andExpect(jsonPath("$.description").value("Prácticas"))
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void duplicateNameIgnoringCaseAnswers409() throws Exception {
        createCategory("Test Simulacros");
        mvc.perform(asAdmin(json(post(URL), "{\"name\":\"TEST SIMULACROS\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.errores[0].campo").value("name"));
    }

    @Test
    void duplicateNameOfAnInactiveCategoryAnswers409() throws Exception {
        String id = createCategory("Test Simulacros");
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asAdmin(json(post(URL), "{\"name\":\"test simulacros\"}")))
                .andExpect(status().isConflict());
    }

    @Test
    void blankNameAnswers400() throws Exception {
        mvc.perform(asAdmin(json(post(URL), "{\"name\":\"  \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("name"));
    }

    @Test
    void updateCanKeepItsOwnName() throws Exception {
        String id = createCategory("Test Alpha");
        mvc.perform(asAdmin(json(put(URL + "/" + id), "{\"name\":\"test alpha\",\"description\":\"Nueva\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test alpha"))
                .andExpect(jsonPath("$.description").value("Nueva"));
    }

    @Test
    void updateToAnotherCategoryNameAnswers409() throws Exception {
        createCategory("Test Alpha");
        String betaId = createCategory("Test Beta");
        mvc.perform(asAdmin(json(put(URL + "/" + betaId), "{\"name\":\"Test Alpha\"}")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUnknownCategoryAnswers404() throws Exception {
        mvc.perform(asAdmin(json(put(URL + "/" + UUID.randomUUID()), "{\"name\":\"Test X\"}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void deactivateTwiceAnswers409() throws Exception {
        String id = createCategory("Test Alpha");
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVO"));
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void reactivateWorksOnlyOnInactiveCategories() throws Exception {
        String id = createCategory("Test Alpha");
        mvc.perform(asAdmin(patch(URL + "/" + id + "/reactivate")))
                .andExpect(status().isConflict());
        mvc.perform(asAdmin(patch(URL + "/" + id + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asAdmin(patch(URL + "/" + id + "/reactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void pageSizeAbove100Answers400() throws Exception {
        mvc.perform(asStudent(get(URL).param("size", "200")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("size"));
    }

    @Test
    void invalidIdAnswers400() throws Exception {
        mvc.perform(asAdmin(patch(URL + "/not-a-uuid/deactivate")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
