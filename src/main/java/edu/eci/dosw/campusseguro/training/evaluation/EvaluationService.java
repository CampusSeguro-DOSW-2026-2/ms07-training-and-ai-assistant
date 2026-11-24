package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.audit.AuditEvent;
import edu.eci.dosw.campusseguro.training.audit.AuditService;
import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryService;
import edu.eci.dosw.campusseguro.training.common.Status;
import edu.eci.dosw.campusseguro.training.exception.BadRequestException;
import edu.eci.dosw.campusseguro.training.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.training.exception.ConflictException;
import edu.eci.dosw.campusseguro.training.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// RF-14 rules: ADMIN manages evaluations and the community answers them.
@Service
@Transactional(readOnly = true)
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private static final String NOT_FOUND = "La evaluación no existe.";

    private final EvaluationRepository repository;
    private final EvaluationAttemptRepository attemptRepository;
    private final CategoryService categoryService;
    private final AuditService auditService;

    public EvaluationService(EvaluationRepository repository, EvaluationAttemptRepository attemptRepository,
                             CategoryService categoryService, AuditService auditService) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.categoryService = categoryService;
        this.auditService = auditService;
    }

    // The community sees active evaluations even when their category is inactive.
    public Page<Evaluation> list(boolean includeInactive, UUID categoryId, Pageable pageable) {
        if (includeInactive) {
            return categoryId == null
                    ? repository.findAll(pageable)
                    : repository.findByCategoryId(categoryId, pageable);
        }
        return categoryId == null
                ? repository.findByStatus(Status.ACTIVO, pageable)
                : repository.findByStatusAndCategoryId(Status.ACTIVO, categoryId, pageable);
    }

    // Loads the questions and options here: open-in-view is off, so the controller cannot load them later.
    public Evaluation get(UUID id) {
        Evaluation evaluation = repository.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        evaluation.getQuestions().forEach(question -> question.getOptions().size());
        return evaluation;
    }

    // An inactive evaluation answers 404 to the community, as if it did not exist.
    public Evaluation getForTaker(UUID id, boolean canManage) {
        Evaluation evaluation = get(id);
        if (!canManage && !evaluation.isActive()) {
            throw new NotFoundException(NOT_FOUND);
        }
        return evaluation;
    }

    @Transactional
    public Evaluation create(UUID userId, EvaluationRequest request) {
        validateQuestions(request.questions());
        Category category = categoryService.requireActive(request.categoryId());
        Evaluation evaluation = new Evaluation(request.title().trim(), request.topic().trim(), category);
        addQuestions(evaluation, request.questions());
        Evaluation saved = repository.save(evaluation);
        auditSafely(userId, AuditEvent.EVALUATION_CREATED,
                "evaluacion:" + saved.getId(), null);
        return saved;
    }

    // The current category can be kept even if it is now inactive; a new one must be active.
    // Questions and options are replaced as a whole, so an evaluation that was already answered is not edited.
    @Transactional
    public Evaluation update(UUID id, UUID userId, EvaluationRequest request) {
        Evaluation evaluation = get(id);
        if (attemptRepository.existsByEvaluationId(id)) {
            throw new ConflictException("La evaluación ya tiene intentos registrados: inactívela y cree una nueva.");
        }
        validateQuestions(request.questions());
        Category category = evaluation.getCategory().getId().equals(request.categoryId())
                ? evaluation.getCategory()
                : categoryService.requireActive(request.categoryId());
        evaluation.update(request.title().trim(), request.topic().trim(), category);
        evaluation.clearQuestions();
        addQuestions(evaluation, request.questions());
        auditSafely(userId, AuditEvent.EVALUATION_UPDATED,
                "evaluacion:" + evaluation.getId(), null);
        return evaluation;
    }

    @Transactional
    public Evaluation deactivate(UUID id) {
        Evaluation evaluation = get(id);
        if (!evaluation.isActive()) {
            throw new ConflictException("La evaluación ya está inactiva.");
        }
        evaluation.deactivate();
        return evaluation;
    }

    @Transactional
    public Evaluation deactivate(UUID id, UUID userId) {
        Evaluation evaluation = deactivate(id);
        auditSafely(userId, AuditEvent.EVALUATION_DEACTIVATED,
                "evaluacion:" + evaluation.getId(), null);
        return evaluation;
    }

    @Transactional
    public Evaluation reactivate(UUID id) {
        Evaluation evaluation = get(id);
        if (evaluation.isActive()) {
            throw new ConflictException("La evaluación ya está activa.");
        }
        evaluation.reactivate();
        return evaluation;
    }

    @Transactional
    public Evaluation reactivate(UUID id, UUID userId) {
        Evaluation evaluation = reactivate(id);
        auditSafely(userId, AuditEvent.EVALUATION_REACTIVATED,
                "evaluacion:" + evaluation.getId(), null);
        return evaluation;
    }

    // Every question must be answered once, with one of its own options. The score is the % of correct answers.
    @Transactional
    public AttemptResponse attempt(UUID id, UUID userId, AttemptRequest request) {
        Evaluation evaluation = get(id);
        if (!evaluation.isActive()) {
            throw new BusinessRuleException("La evaluación no está activa.");
        }
        Map<UUID, UUID> chosen = new HashMap<>();
        for (AttemptRequest.AnswerRequest answer : request.answers()) {
            if (chosen.put(answer.questionId(), answer.optionId()) != null) {
                throw new BadRequestException("answers", "Cada pregunta se responde una sola vez.");
            }
        }

        List<AttemptResponse.AnswerResult> results = new ArrayList<>();
        int correctAnswers = 0;
        for (Question question : evaluation.getQuestions()) {
            UUID optionId = chosen.remove(question.getId());
            if (optionId == null) {
                throw new BadRequestException("answers", "Falta responder la pregunta " + question.getPosition() + ".");
            }
            QuestionOption correctOption = question.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .findFirst()
                    .orElseThrow();
            boolean belongs = question.getOptions().stream().anyMatch(o -> o.getId().equals(optionId));
            if (!belongs) {
                throw new BadRequestException("answers",
                        "La opción elegida no pertenece a la pregunta " + question.getPosition() + ".");
            }
            boolean correct = correctOption.getId().equals(optionId);
            if (correct) {
                correctAnswers++;
            }
            results.add(new AttemptResponse.AnswerResult(question.getId(), optionId, correct, correctOption.getId()));
        }
        if (!chosen.isEmpty()) {
            throw new BadRequestException("answers", "Hay respuestas a preguntas que no son de esta evaluación.");
        }

        int total = evaluation.getQuestions().size();
        int score = Math.round(correctAnswers * 100f / total);
        EvaluationAttempt attempt = attemptRepository.save(new EvaluationAttempt(userId, evaluation, score));
        auditSafely(userId, AuditEvent.ATTEMPT_REGISTERED,
                "intento:" + attempt.getId(),
                Map.of("evaluacion_id", evaluation.getId().toString()));
        return new AttemptResponse(attempt.getId(), evaluation.getId(), attempt.getTopic(), score, correctAnswers,
                total, attempt.getAttemptedAt(), results);
    }

    private void auditSafely(
            UUID userId,
            String eventType,
            String resource,
            Map<String, Object> attributes
    ) {
        try {
            auditService.register(userId, eventType, resource, attributes);
        } catch (RuntimeException exception) {
            log.warn("Could not persist audit event {}", eventType, exception);
        }
    }

    private static void validateQuestions(List<EvaluationRequest.QuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessRuleException("La evaluación debe tener al menos una pregunta.");
        }
        for (int i = 0; i < questions.size(); i++) {
            List<EvaluationRequest.OptionRequest> options = questions.get(i).options();
            int number = i + 1;
            if (options == null || options.size() < 2) {
                throw new BusinessRuleException("La pregunta " + number + " debe tener al menos dos opciones.");
            }
            long correct = options.stream().filter(EvaluationRequest.OptionRequest::correct).count();
            if (correct != 1) {
                throw new BusinessRuleException("La pregunta " + number + " debe tener exactamente una opción correcta.");
            }
        }
    }

    private static void addQuestions(Evaluation evaluation, List<EvaluationRequest.QuestionRequest> questions) {
        for (EvaluationRequest.QuestionRequest request : questions) {
            Question question = evaluation.addQuestion(request.statement().trim());
            request.options().forEach(option -> question.addOption(option.text().trim(), option.correct()));
        }
    }
}
