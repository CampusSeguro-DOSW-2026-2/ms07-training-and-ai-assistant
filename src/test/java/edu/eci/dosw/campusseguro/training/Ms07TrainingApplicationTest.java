package edu.eci.dosw.campusseguro.training;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Starts the whole context on H2 and runs the Flyway migrations.
@SpringBootTest
@ActiveProfiles("test")
class Ms07TrainingApplicationTest {

    @Test
    void contextLoads() {
    }
}
