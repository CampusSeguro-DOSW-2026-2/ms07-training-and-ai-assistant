package edu.eci.dosw.campusseguro.training.tutor.ai;

import edu.eci.dosw.campusseguro.training.tutor.TutorSafetyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TutorProperties.class, TutorSafetyProperties.class, EmbeddingProperties.class,
        ModerationProperties.class, AiResilienceProperties.class})
public class TutorAiConfig {
}
