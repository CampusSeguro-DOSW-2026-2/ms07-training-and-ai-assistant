package edu.eci.dosw.campusseguro.training.progress;

import edu.eci.dosw.campusseguro.training.evaluation.Evaluation;
import edu.eci.dosw.campusseguro.training.evaluation.EvaluationAttempt;
import edu.eci.dosw.campusseguro.training.evaluation.EvaluationAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// RF-13: progress of a user, only from their own data (rule MR-02).
@Service
@Transactional(readOnly = true)
public class ProgressService {

    // Higher score first; on a tie, the most recent attempt.
    private static final Comparator<EvaluationAttempt> BEST =
            Comparator.comparingInt(EvaluationAttempt::getScore).thenComparing(EvaluationAttempt::getAttemptedAt);

    private final ContentViewRepository viewRepository;
    private final EvaluationAttemptRepository attemptRepository;

    public ProgressService(ContentViewRepository viewRepository, EvaluationAttemptRepository attemptRepository) {
        this.viewRepository = viewRepository;
        this.attemptRepository = attemptRepository;
    }

    public ProgressResponse of(UUID userId) {
        List<ProgressResponse.ViewedContent> contents = viewRepository.findByUserIdWithContent(userId).stream()
                .map(view -> new ProgressResponse.ViewedContent(view.getContent().getId(), view.getContent().getTitle(),
                        view.getContent().getCategory().getId(), view.getFirstViewedAt(), view.getLastViewedAt(),
                        view.getContent().isVisibleToCommunity()))
                .toList();

        Map<UUID, List<EvaluationAttempt>> byEvaluation = attemptRepository.findByUserIdWithEvaluation(userId).stream()
                .collect(Collectors.groupingBy(attempt -> attempt.getEvaluation().getId()));
        List<ProgressResponse.EvaluationResult> evaluations = byEvaluation.values().stream()
                .map(attempts -> {
                    EvaluationAttempt best = attempts.stream().max(BEST).orElseThrow();
                    Evaluation evaluation = best.getEvaluation();
                    return new ProgressResponse.EvaluationResult(evaluation.getId(), evaluation.getTitle(),
                            evaluation.getCategory().getId(), best.getTopic(), best.getScore(), best.getAttemptedAt(),
                            attempts.size(), evaluation.isActive());
                })
                .sorted(Comparator.comparing(ProgressResponse.EvaluationResult::attemptedAt).reversed())
                .toList();

        return new ProgressResponse(contents, evaluations);
    }
}
