package edu.eci.dosw.campusseguro.training.progress;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Views are written in their own transactions, so these tests commit their data and delete it afterwards.
// The texts avoid the words the tutor tests search for.
@SpringBootTest
@ActiveProfiles("test")
class ContentViewControllerTest {

    private static final String CONTENTS = "/api/v1/training/contents";
    private static final String CATEGORIES = "/api/v1/training/categories";
    private static final String STUDENT = "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilter;

    @Autowired
    private ContentViewRepository views;

    @Autowired
    private ContentViewService service;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private final List<String> categoryIds = new ArrayList<>();
    private final List<String> contentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @AfterEach
    void cleanUp() {
        contentIds.forEach(id -> jdbc.update("DELETE FROM content_view WHERE content_id = ?", UUID.fromString(id)));
        contentIds.forEach(id -> jdbc.update("DELETE FROM content WHERE id = ?", UUID.fromString(id)));
        categoryIds.forEach(id -> jdbc.update("DELETE FROM category WHERE id = ?", UUID.fromString(id)));
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42").header("X-User-Role", "ADMIN");
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", STUDENT).header("X-User-Role", "ESTUDIANTE");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String createContent() throws Exception {
        String category = mvc.perform(asAdmin(json(post(CATEGORIES), "{\"name\":\"Prueba vistas " + UUID.randomUUID() + "\"}")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String categoryId = JsonPath.read(category, "$.id");
        categoryIds.add(categoryId);
        String content = mvc.perform(asAdmin(json(post(CONTENTS),
                        "{\"title\":\"Prueba vistas\",\"body\":\"Registro de vistas.\",\"categoryId\":\"" + categoryId + "\"}")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String contentId = JsonPath.read(content, "$.id");
        contentIds.add(contentId);
        return contentId;
    }

    private static String views(String contentId) {
        return CONTENTS + "/" + contentId + "/views";
    }

    @Test
    void firstViewCreatesTheRecord() throws Exception {
        String contentId = createContent();
        mvc.perform(asStudent(post(views(contentId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentId").value(contentId))
                .andExpect(jsonPath("$.firstViewedAt").exists())
                .andExpect(jsonPath("$.lastViewedAt").exists());
    }

    @Test
    void twoViewsKeepOneRecordWithTheNewDate() throws Exception {
        String contentId = createContent();
        String first = mvc.perform(asStudent(post(views(contentId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Thread.sleep(5);
        String second = mvc.perform(asStudent(post(views(contentId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(views.countByUserIdAndContentId(UUID.fromString(STUDENT), UUID.fromString(contentId))).isEqualTo(1);
        String firstSeen = JsonPath.read(first, "$.firstViewedAt");
        assertThat((String) JsonPath.read(second, "$.firstViewedAt")).isEqualTo(firstSeen);
        assertThat(Instant.parse(JsonPath.read(second, "$.lastViewedAt")))
                .isAfter(Instant.parse(JsonPath.read(first, "$.lastViewedAt")));
    }

    @Test
    void simultaneousFirstViewsKeepOneRecord() throws Exception {
        String contentId = createContent();
        UUID user = UUID.randomUUID();
        UUID content = UUID.fromString(contentId);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<ContentViewService.Result>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> service.register(user, content));
            }
            List<Future<ContentViewService.Result>> results = pool.invokeAll(calls);
            long created = 0;
            for (Future<ContentViewService.Result> result : results) {
                if (result.get().created()) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(views.countByUserIdAndContentId(user, content)).isEqualTo(1);
    }

    @Test
    void eachUserHasItsOwnRecord() throws Exception {
        String contentId = createContent();
        mvc.perform(asStudent(post(views(contentId)))).andExpect(status().isCreated());
        mvc.perform(post(views(contentId)).header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ESTUDIANTE"))
                .andExpect(status().isCreated());
    }

    @Test
    void inactiveContentAnswers422AndIsNotRegistered() throws Exception {
        String contentId = createContent();
        mvc.perform(asAdmin(patch(CONTENTS + "/" + contentId + "/deactivate"))).andExpect(status().isOk());
        mvc.perform(asStudent(post(views(contentId))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        assertThat(views.countByUserIdAndContentId(UUID.fromString(STUDENT), UUID.fromString(contentId))).isZero();
    }

    @Test
    void unknownContentAnswers404() throws Exception {
        mvc.perform(asStudent(post(views(UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void adminWithoutReadPermissionCannotRegisterViews() throws Exception {
        String contentId = createContent();
        mvc.perform(asAdmin(post(views(contentId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidIdAnswers400() throws Exception {
        mvc.perform(asStudent(post(views("not-a-uuid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
