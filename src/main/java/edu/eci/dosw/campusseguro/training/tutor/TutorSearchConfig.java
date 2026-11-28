package edu.eci.dosw.campusseguro.training.tutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(TutorSearchProperties.class)
public class TutorSearchConfig {

    public static final String EMBEDDING_EXECUTOR = "contentEmbeddingExecutor";

    // One thread: embeddings are calculated one batch after another, never in the request of a user.
    @Bean(name = EMBEDDING_EXECUTOR)
    public ThreadPoolTaskExecutor contentEmbeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("content-embedding-");
        executor.initialize();
        return executor;
    }
}
