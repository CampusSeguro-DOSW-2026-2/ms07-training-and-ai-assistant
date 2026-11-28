package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryRepository;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The tutor covers every campus emergency in the contents, not only earthquakes. The contents are the ones of
// V4__seed_contents.sql, with the same words.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TutorEmergencyScopeTest {

    private static final String SPILL = "Qué hacer ante un derrame o una fuga de sustancias";
    private static final String LABORATORY = "Seguridad en laboratorios ante una emergencia";
    private static final String EVACUATE = "Cómo evacuar con orden";
    private static final String CORRIDORS = "Mantén libres las salidas y los pasillos del salón";
    private static final String AFTER = "Primeras acciones después de un sismo";
    private static final String DURING = "Qué hacer durante un sismo si estás en un espacio cerrado";

    @Autowired
    private ContentRetriever retriever;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ContentRepository contents;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categories.save(new Category("Test Tutor Emergencias", null));
        content(LABORATORY, "Si ocurre una emergencia en un laboratorio, detén lo que estás haciendo, apaga los equipos "
                + "que puedas hacerlo de forma segura y cierra las llaves de gas y los recipientes con reactivos. Luego "
                + "evacua con orden siguiendo la ruta señalizada. Conoce la ubicación de las duchas de emergencia, los "
                + "lavaojos y los extintores antes de empezar a trabajar.");
        content(SPILL, "Si se derrama o se fuga una sustancia, avisa de inmediato al responsable del laboratorio y a "
                + "los brigadistas. No intentes limpiarla si no tienes el entrenamiento ni los elementos de protección "
                + "adecuados. Aléjate de la zona, ventila si es posible hacerlo sin riesgo y no permitas que otras "
                + "personas se acerquen hasta que el personal autorizado indique que es seguro.");
        content(EVACUATE, "Al recibir la orden de evacuar, camina sin correr, sin empujar y sin devolverte por objetos "
                + "personales. Usa las escaleras y nunca el ascensor. Mantén la derecha y deja libre el paso para "
                + "quienes necesiten apoyo. Sigue la señalización de evacuación y las indicaciones de los brigadistas "
                + "hasta llegar al punto de encuentro.");
        content(CORRIDORS, "Mantén los pasillos y las puertas del salón libres de mochilas, sillas y otros objetos que "
                + "puedan estorbar durante una evacuación. Identifica las dos salidas más cercanas desde tu puesto y la "
                + "ruta hacia el punto de encuentro. Informa al personal de la universidad si ves una salida bloqueada "
                + "o mobiliario que pueda caer.");
        content(AFTER, "Cuando el movimiento termine, revisa si tú o las personas cercanas tienen lesiones y avisa a "
                + "los brigadistas. No muevas a quien tenga una lesión grave salvo que haya peligro inmediato. Ten en "
                + "cuenta que pueden presentarse réplicas. No uses fósforos ni encendedores por posibles fugas de gas, "
                + "y atiende siempre las instrucciones de brigadistas y coordinadores.");
        content(DURING, "Mantén la calma y no corras hacia la salida. Agáchate, cúbrete debajo de una mesa resistente o "
                + "junto a una pared interna y sujétate hasta que el movimiento termine. Aléjate de ventanas, vidrios, "
                + "lámparas y estantes que puedan caer. No uses los ascensores. Cuando el temblor pare, espera las "
                + "indicaciones de los brigadistas y evacua con orden.");
    }

    private void content(String title, String body) {
        contents.save(new Content(title, body, category));
    }

    private List<String> titles(String question) {
        return retriever.find(question).stream().map(RelatedContent::title).toList();
    }

    @Test
    void aChemicalSpilledInTheLabFindsTheSpillContentFirst() {
        assertThat(titles("¿Qué hago si se riega un químico en el lab?")).startsWith(SPILL);
    }

    @Test
    void otherWordsForSpillsAndLeaksFindItToo() {
        assertThat(titles("se regó un reactivo en el laboratorio")).startsWith(SPILL);
        assertThat(titles("hay un escape de químicos")).startsWith(SPILL);
        assertThat(titles("¿cómo actúo ante derrames de sustancias?")).startsWith(SPILL);
    }

    @Test
    void smokeInTheCorridorFindsTheEvacuationContents() {
        assertThat(titles("hay humo en el pasillo")).contains(CORRIDORS);
    }

    @Test
    void smokeFindsTheFireContentWhenThereIsOne() {
        content("Qué hacer en caso de incendio", "Si ves fuego o humo, activa la alarma, no uses el ascensor y "
                + "evacúa por la ruta señalizada. Usa el extintor solo si sabes hacerlo.");

        assertThat(titles("hay humo en el pasillo")).contains("Qué hacer en caso de incendio", CORRIDORS);
        assertThat(titles("¿Qué hago si veo llamas?")).containsExactly("Qué hacer en caso de incendio");
        assertThat(titles("¿dónde están los extintores si hay fuego?")).startsWith("Qué hacer en caso de incendio");
        assertThat(titles("sonó la sirena de incendio")).startsWith("Qué hacer en caso de incendio");
    }

    @Test
    void anInjuredPersonFindsTheContentAboutInjuries() {
        assertThat(titles("¿Qué hago si alguien está herido?")).contains(AFTER);
        assertThat(titles("una compañera se desmayó")).contains(AFTER);
        assertThat(titles("un compañero tiene sangre en la cabeza")).contains(AFTER);
    }

    @Test
    void earthquakesStillWork() {
        assertThat(titles("¿Qué hago si tiembla mientras estoy en clase?")).contains(DURING);
    }

    @Test
    void outOfScopeQuestionsStillFindNothing() {
        assertThat(retriever.find("receta de pasta")).isEmpty();
        assertThat(retriever.find("¿Me das una receta de pasta con queso?")).isEmpty();
        assertThat(retriever.find("¿Quién ganó el partido de fútbol?")).isEmpty();
    }
}
