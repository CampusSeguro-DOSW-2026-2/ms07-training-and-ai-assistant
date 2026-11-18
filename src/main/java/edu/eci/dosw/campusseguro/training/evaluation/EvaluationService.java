package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.category.CategoryService;
import edu.eci.dosw.campusseguro.training.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.training.exception.ConflictException;
import edu.eci.dosw.campusseguro.training.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// RF-14 rules for managing evaluations.
@Service
@Transactional(readOnly = true)
public class EvaluationService {

    private final EvaluationRepository repository;
    private final CategoryService categoryService;

    public EvaluationService(EvaluationRepository repository, CategoryService categoryService) {
        this.repository = repository;
        this.categoryService = categoryService;
    }

    public Evaluation get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("La evaluación no existe."));
    }

    @Transactional
    public Evaluation create(EvaluationRequest request) {
        validateQuestions(request.questions());
        Category category = categoryService.requireActive(request.categoryId());
        Evaluation evaluation = new Evaluation(request.title().trim(), request.topic().trim(), category);
        addQuestions(evaluation, request.questions());
        return repository.save(evaluation);
    }

    // The current category can be kept even if it is now inactive; a new one must be active.
    // Questions and options are replaced as a whole.
    @Transactional
    public Evaluation update(UUID id, EvaluationRequest request) {
        Evaluation evaluation = get(id);
        validateQuestions(request.questions());
        Category category = evaluation.getCategory().getId().equals(request.categoryId())
                ? evaluation.getCategory()
                : categoryService.requireActive(request.categoryId());
        evaluation.update(request.title().trim(), request.topic().trim(), category);
        evaluation.clearQuestions();
        addQuestions(evaluation, request.questions());
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
    public Evaluation reactivate(UUID id) {
        Evaluation evaluation = get(id);
        if (evaluation.isActive()) {
            throw new ConflictException("La evaluación ya está activa.");
        }
        evaluation.reactivate();
        return evaluation;
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
