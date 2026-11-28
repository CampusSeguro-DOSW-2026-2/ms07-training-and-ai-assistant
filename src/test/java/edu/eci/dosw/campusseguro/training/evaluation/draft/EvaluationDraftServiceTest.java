package edu.eci.dosw.campusseguro.training.evaluation.draft;

import edu.eci.dosw.campusseguro.training.audit.AuditService;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryService;
import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.content.Content;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import edu.eci.dosw.campusseguro.training.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Cases that are hard to reach through HTTP: a full queue and an unexpected failure.
class EvaluationDraftServiceTest {

    private static final UUID ADMIN = UUID.randomUUID();

    private final CategoryService categories = mock(CategoryService.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final EvaluationDraftGenerator generator = mock(EvaluationDraftGenerator.class);
    private final AuditService audit = mock(AuditService.class);
    private final EvaluationDraftStore store = new EvaluationDraftStore();
    private final Category category = new Category("Laboratorios", null);

    private EvaluationDraftService service(java.util.concurrent.Executor executor) {
        when(categories.get(category.getId())).thenReturn(category);
        when(contents.findByStatusAndCategoryIdOrderByTitle(Status.ACTIVO, category.getId()))
                .thenReturn(List.of(new Content("Derrames", "Avisa al responsable.", category)));
        return new EvaluationDraftService(categories, contents, generator, store, audit, executor, Clock.systemUTC());
    }

    private EvaluationDraftRequest request() {
        return new EvaluationDraftRequest(category.getId(), 5, null);
    }

    @Test
    void aFullQueueAnswers503AndKeepsNoDraft() {
        EvaluationDraftService service = service(command -> {
            throw new TaskRejectedException("full");
        });

        assertThatThrownBy(() -> service.start(ADMIN, request()))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage(EvaluationDraftService.BUSY);
        assertThat(store.size()).isZero();
        verify(audit, never()).register(any(), any());
    }

    @Test
    void anUnexpectedErrorEndsFailedWithoutDetails() {
        when(generator.generate(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("texto interno del modelo"));
        EvaluationDraftService service = service(Runnable::run);

        EvaluationDraftResponse started = service.start(ADMIN, request());

        EvaluationDraftResponse draft = service.get(started.id(), ADMIN);
        assertThat(draft.status()).isEqualTo(DraftStatus.FALLIDO);
        assertThat(draft.message()).isEqualTo(EvaluationDraftService.UNEXPECTED).doesNotContain("texto interno");
    }

    @Test
    void otherAdminsGet404() {
        EvaluationDraftService service = service(command -> {
        });
        EvaluationDraftResponse started = service.start(ADMIN, request());

        assertThatThrownBy(() -> service.get(started.id(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(EvaluationDraftService.NOT_FOUND);
    }
}
