package edu.eci.dosw.campusseguro.training.tutor;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import({
        TutorControllerTest.FakeAiConfig.class,
        TutorContentDatabaseFailureTest.FailingRepositoryConfig.class
})
class TutorContentDatabaseFailureTest {

    private static final String USER = "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19";

    @TestConfiguration
    static class FailingRepositoryConfig {

        @Bean
        @Primary
        TutorContentRepository failingTutorContentRepository() {
            return status -> {
                throw new DataAccessResourceFailureException("Simulated database failure");
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

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilter)
                .build();
        ai.reset();
    }

    @Test
    void databaseFailureReturns503WithoutCallingAi() throws Exception {
        mvc.perform(post("/api/v1/tutor/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Qué hago si hay un sismo?\"}")
                        .header("X-User-Id", USER)
                        .header("X-User-Role", "ESTUDIANTE")
                        .header("X-User-Permission",
                                "ms07:content:read,ms07:quiz:take,ms07:tutor:use"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje")
                        .value("El tutor no está disponible en este momento. "
                                + "Intenta de nuevo o consulta el centro de aprendizaje."));

        assertThat(ai.calls).isZero();
    }
}
