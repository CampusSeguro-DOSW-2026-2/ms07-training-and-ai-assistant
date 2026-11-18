package edu.eci.dosw.campusseguro.training;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Starts the whole context on H2. Flyway is off in tests (Hibernate creates the schema),
// so migrations are only checked when the app starts against PostgreSQL.
@SpringBootTest
@ActiveProfiles("test")
class Ms07TrainingApplicationTest {

    @Test
    void contextLoads() {
    }
}
