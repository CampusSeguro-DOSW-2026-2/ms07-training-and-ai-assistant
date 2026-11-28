package edu.eci.dosw.campusseguro.training.evaluation.draft;

import edu.eci.dosw.campusseguro.training.audit.AuditEvent;
import edu.eci.dosw.campusseguro.training.audit.AuditService;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryService;
import edu.eci.dosw.campusseguro.training.common.SlidingWindowRateLimiter;
import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.content.ContentRepository;
import edu.eci.dosw.campusseguro.training.evaluation.EvaluationRequest;
import edu.eci.dosw.campusseguro.training.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.training.exception.DependencyUnavailableException;
import edu.eci.dosw.campusseguro.training.exception.NotFoundException;
import edu.eci.dosw.campusseguro.training.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

// RF-14: the ADMIN asks the AI for a draft evaluation of a category and polls for it. Writing 3 to 10 questions
// can take longer than the API Gateway waits, so the request only starts the work (202) and the draft is read
// later with GET. The AI never saves anything: the ADMIN reviews the draft and saves it with
// POST /api/v1/training/evaluations.
@Service
public class EvaluationDraftService {

    public static final int DRAFTS_PER_HOUR = 5;

    static final String NOT_FOUND = "El borrador no existe o ya venció.";
    static final String TOO_MANY =
            "Ya pidió " + DRAFTS_PER_HOUR + " borradores en la última hora. Espere un rato y vuelva a intentarlo.";
    static final String BUSY =
            "Hay muchas evaluaciones generándose en este momento. Intente de nuevo en unos minutos.";
    static final String AI_UNAVAILABLE =
            "No fue posible generar el borrador con la IA en este momento. Intente de nuevo en unos minutos.";
    static final String UNEXPECTED = "Ocurrió un error inesperado al generar el borrador. Intente de nuevo.";

    private static final Logger log = LoggerFactory.getLogger(EvaluationDraftService.class);

    private final CategoryService categories;
    private final ContentRepository contents;
    private final EvaluationDraftGenerator generator;
    private final EvaluationDraftStore store;
    private final AuditService audit;
    private final Executor executor;
    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public EvaluationDraftService(CategoryService categories, ContentRepository contents,
                                  EvaluationDraftGenerator generator, EvaluationDraftStore store, AuditService audit,
                                  @Qualifier(EvaluationDraftConfig.EXECUTOR) Executor executor) {
        this(categories, contents, generator, store, audit, executor, Clock.systemUTC());
    }

    EvaluationDraftService(CategoryService categories, ContentRepository contents,
                           EvaluationDraftGenerator generator, EvaluationDraftStore store, AuditService audit,
                           Executor executor, Clock clock) {
        this.categories = categories;
        this.contents = contents;
        this.generator = generator;
        this.store = store;
        this.audit = audit;
        this.executor = executor;
        this.limiter = new SlidingWindowRateLimiter(clock,
                List.of(new SlidingWindowRateLimiter.Window(Duration.ofHours(1), DRAFTS_PER_HOUR)));
    }

    // 404 if the category does not exist; 422 if it is inactive or has no active contents; 429 over the limit;
    // 503 if the queue is full. The contents are read here, so the background work does not touch the database.
    @Transactional(readOnly = true)
    public EvaluationDraftResponse start(UUID userId, EvaluationDraftRequest request) {
        Category category = categories.get(request.categoryId());
        if (!category.isActive()) {
            throw new BusinessRuleException("La categoría '" + category.getName() + "' está inactiva.");
        }
        List<EvaluationDraftGenerator.Source> sources = contents
                .findByStatusAndCategoryIdOrderByTitle(Status.ACTIVO, category.getId()).stream()
                .map(content -> new EvaluationDraftGenerator.Source(content.getId(), content.getTitle(),
                        content.getBody()))
                .toList();
        if (sources.isEmpty()) {
            throw new BusinessRuleException("La categoría '" + category.getName()
                    + "' no tiene contenidos activos para generar preguntas.");
        }
        if (limiter.tryAcquire(userId.toString()).isPresent()) {
            throw new TooManyRequestsException(TOO_MANY);
        }

        EvaluationDraftStore.Draft draft = store.create(userId);
        UUID categoryId = category.getId();
        String categoryName = category.getName();
        try {
            executor.execute(() -> generate(draft.id(), categoryId, categoryName, request, sources));
        } catch (TaskRejectedException e) {
            store.remove(draft.id());
            log.warn("Evaluation draft queue is full");
            throw new DependencyUnavailableException(BUSY);
        }
        register(userId);
        return EvaluationDraftResponse.from(draft);
    }

    public EvaluationDraftResponse get(UUID id, UUID userId) {
        return store.find(id, userId)
                .map(EvaluationDraftResponse::from)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    // Runs in the draft executor. Never lets an exception out: the draft ends LISTO or FALLIDO.
    private void generate(UUID draftId, UUID categoryId, String categoryName, EvaluationDraftRequest request,
                          List<EvaluationDraftGenerator.Source> sources) {
        try {
            EvaluationRequest result = generator.generate(categoryId, categoryName, request.questionCount(),
                    request.cleanTopic(), sources);
            store.ready(draftId, result);
        } catch (EvaluationDraftGenerator.DraftRejectedException e) {
            store.failed(draftId, e.getMessage());
        } catch (DependencyUnavailableException e) {
            store.failed(draftId, AI_UNAVAILABLE);
        } catch (RuntimeException e) {
            log.error("Evaluation draft {} failed: {}", draftId, e.getClass().getSimpleName());
            store.failed(draftId, UNEXPECTED);
        }
    }

    // Only who asked and when; the draft itself is never recorded.
    private void register(UUID userId) {
        try {
            audit.register(userId, AuditEvent.EVALUATION_DRAFTED);
        } catch (RuntimeException e) {
            log.warn("Could not persist audit event {}: {}", AuditEvent.EVALUATION_DRAFTED,
                    e.getClass().getSimpleName());
        }
    }
}
