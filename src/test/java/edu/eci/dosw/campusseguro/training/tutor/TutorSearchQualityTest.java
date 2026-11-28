package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapter;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiAdapters;
import edu.eci.dosw.campusseguro.training.tutor.ai.AiPrompt;
import edu.eci.dosw.campusseguro.training.tutor.ai.TutorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// How the tutor search behaves with real questions: badly written, with synonyms or out of scope.
// Contents: the seeds of V4__seed_contents.sql and V11__seed_more_contents.sql, with their real texts.
// Embeddings come from a fake, deterministic model (a hashed bag of the roots of the text), so the result is
// always the same and no provider is called.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TutorSearchQualityTest {

    static final String SPILL = "Qué hacer ante un derrame o una fuga de sustancias";
    static final String LAB = "Seguridad en laboratorios ante una emergencia";
    static final String EVACUATE = "Cómo evacuar con orden";
    static final String MEETING_POINT = "El punto de encuentro y el conteo de personas";
    static final String INDOORS = "Qué hacer durante un sismo si estás en un espacio cerrado";
    static final String AFTER = "Primeras acciones después de un sismo";
    static final String AFTERSHOCKS = "Réplicas y regreso a las instalaciones";
    static final String KIT = "Prepara tus contactos de emergencia y tu kit básico";
    static final String CLASSROOM = "Qué hacer en un salón de clase durante una emergencia";
    static final String CLASSROOM_EXITS = "Mantén libres las salidas y los pasillos del salón";
    static final String AUDITORIUM = "Cómo evacuar un auditorio";
    static final String AUDITORIUM_PREVENTION = "Prevención en auditorios: salidas libres y aforo";
    static final String OFFICE = "Orden preventivo en la oficina";
    static final String FIRE = "Qué hacer durante un incendio";
    static final String ELEVATOR = "Si quedas atrapado en un ascensor";
    static final String POWER = "Qué hacer ante un corte de energía";
    static final String THREAT = "Objeto sospechoso o amenaza en el campus";
    static final String FIRST_AID = "Primeros auxilios mientras llega la brigada: desmayo y heridas";
    static final String FLOOD = "Inundación o filtración de agua";
    static final String AFTER_FIRE = "Después de un incendio";
    static final String FIRE_CLASSROOM = "Fuego o humo cerca de tu salón";
    static final String OFFICE_FIRE = "Prevención de incendios en la oficina";
    static final String PREVENT_FIRE = "Prevenir incendios y usar un extintor";
    static final String LAB_FIRE = "Un incendio pequeño en el laboratorio";
    static final String LAB_GAS = "Olor a gas en el laboratorio: actúa sin chispas";
    static final String GAS = "Fuga de gas: señales y qué no hacer";
    static final String MOBILITY = "Evacuar con personas con movilidad reducida";
    static final String DRILLS = "Para qué sirven los simulacros y cómo participar";
    static final String BURNS = "Quemaduras leves y lesionados graves: qué hacer y qué no";
    // A question about a fire in general can be answered with any of these
    static final String ANY_FIRE = FIRE + ";" + AFTER_FIRE + ";" + FIRE_CLASSROOM + ";" + OFFICE_FIRE + ";"
            + PREVENT_FIRE + ";" + LAB_FIRE;

    // A model that only knows the roots of the words: two texts are close when they share roots.
    static class HashingEmbeddings implements AiAdapter {
        static final int DIMENSIONS = 512;

        @Override
        public String provider() {
            return "fake-embeddings";
        }

        @Override
        public String complete(AiPrompt prompt) {
            return "Respuesta de prueba.";
        }

        @Override
        public boolean supportsEmbeddings() {
            return true;
        }

        @Override
        public String embeddingModel() {
            return "fake-hashing";
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(HashingEmbeddings::vector).toList();
        }

        static float[] vector(String text) {
            float[] vector = new float[DIMENSIONS];
            for (String stem : ContentRetriever.stems(text)) {
                vector[Math.floorMod(stem.hashCode(), DIMENSIONS)] += 1;
            }
            return vector;
        }
    }

    @TestConfiguration
    static class FakeEmbeddingsConfig {

        @Bean
        @Primary
        AiAdapters hashingAdapters() {
            return new AiAdapters(List.of(new HashingEmbeddings()),
                    new TutorProperties("fake-embeddings", null, null, null, null, null, null));
        }
    }

    @Autowired
    private ContentRetriever retriever;

    @Autowired
    private ContentEmbeddings embeddings;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ContentRepository contents;

    @BeforeEach
    void setUp() {
        Category category = categories.save(new Category("Test Búsqueda", null));
        for (String migration : List.of("V4__seed_contents.sql", "V11__seed_more_contents.sql")) {
            SeedContents.read(migration).forEach(seed -> contents.save(new Content(seed.title(), seed.body(), category)));
        }
        contents.flush();
        embeddings.refreshAll();
    }

    private List<String> titles(String question) {
        String redacted = new PersonalDataRedactor().redact(question);
        float[] vector = embeddings.embedQuestion(redacted).orElse(null);
        return retriever.find(question, vector).stream().map(RelatedContent::title).toList();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "hay fugs de gas en el lab | " + SPILL + ";" + LAB_GAS,
            "hay una fuja de gas en el laboratorio | " + SPILL + ";" + LAB_GAS,
            "olor a gas en la oficina | " + GAS + ";" + SPILL,
            "se regó un ácido en el laboratorio | " + SPILL,
            "derrame quimico | " + SPILL,
            "se salió un solvente | " + SPILL,
            "duchas de emergencia en el lab | " + LAB,
            "dónde está el extintor del laboratorio | " + LAB,
            "se prendió fuego la cafetería | " + ANY_FIRE,
            "hay humo en el pasillo | " + FIRE + ";" + FIRE_CLASSROOM,
            "incendo en el auditorio | " + ANY_FIRE,
            "conato de incendio en la oficina | " + OFFICE_FIRE,
            "se prendió un mechero en el laboratorio | " + LAB_FIRE,
            "un compañero se desmayó | " + FIRST_AID,
            "mi compañero está sangrando | " + FIRST_AID,
            "un estudiante tiene una fractura | " + BURNS,
            "me quemé la mano con agua caliente | " + BURNS,
            "alguien quedó inconsciente | " + FIRST_AID,
            "me quedé atrapado en el elevador | " + ELEVATOR,
            "se fue la luz en el salón | " + POWER,
            "hay un apagón | " + POWER,
            "hay un paquete sospechoso en el pasillo | " + THREAT,
            "vi una persona armada | " + THREAT,
            "se está inundando el salón | " + FLOOD,
            "hay una filtracion de agua | " + FLOOD,
            "qué hago si hay un sizmo | " + INDOORS,
            "lamparas que caen en un temblor | " + INDOORS,
            "terremoto en clase | " + CLASSROOM + ";" + INDOORS,
            "cómo ayudo a alguien en silla de ruedas a evacuar | " + MOBILITY,
            "para qué sirve un simulacro | " + DRILLS,
            "salidas bloqueadas en el aula | " + CLASSROOM_EXITS,
            "como evaquar el edificio | " + EVACUATE,
            "debo desalojar con orden | " + EVACUATE,
            "dónde es el punto de encuentro | " + MEETING_POINT,
            "cuál es la zona segura | " + MEETING_POINT,
            "réplicas después del sismo | " + AFTERSHOCKS,
            "hay heridos después del temblor | " + AFTER,
            "kit de emergencia | " + KIT,
            "como evacúo un auditorio | " + AUDITORIUM,
            "aforo del teatro | " + AUDITORIUM_PREVENTION,
            "archivadores de la oficina | " + OFFICE,
    })
    void findsTheExpectedContent(String question, String expected) {
        // Several titles separated by ";" are alternatives: any of them is a good answer
        assertThat(titles(question)).containsAnyElementsOf(List.of(expected.split(";")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "receta de pasta",
            "¿Quién ganó el partido de fútbol?",
            "cuál es la capital de Francia",
            "cuéntame un chiste",
            "cómo está el clima hoy",
            "recomiéndame una película",
            "cuánto cuesta la matrícula",
            "horario de la biblioteca",
            "cómo programo en Java",
            "qué es la fotosíntesis",
            "mi gato está enfermo",
            "pasa la tarea de cálculo",
            "dónde queda la cafetería",
            "quiero comprar un celular",
            "hola, cómo estás",
    })
    void outOfScopeQuestionsFindNothing(String question) {
        assertThat(titles(question)).isEmpty();
    }

    @Test
    void theWholeSearchTakesAFewMilliseconds() {
        titles("hay fugs de gas en el lab");

        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            titles("se prendió fuego la cafetería y hay humo en el pasillo");
        }
        long averageMs = (System.nanoTime() - start) / 20 / 1_000_000;

        assertThat(averageMs).isLessThan(300);
    }
}
