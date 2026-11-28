package edu.eci.dosw.campusseguro.training.evaluation.draft;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(EvaluationDraftProperties.class)
public class EvaluationDraftConfig {

    public static final String EXECUTOR = "evaluationDraftExecutor";

    // Small on purpose: at most 2 drafts are written at once and 10 wait. When it is full the request answers 503.
    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor evaluationDraftExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("evaluation-draft-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
