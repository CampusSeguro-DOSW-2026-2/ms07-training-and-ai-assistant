package edu.eci.dosw.campusseguro.training.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Identity comes from the Gateway in X-User-Id and X-User-Role. MS07 does not validate tokens:
// it authorizes by permission.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Identity comes from the Gateway headers, so there are no local users. An empty manager
    // stops Spring Boot from creating a default user with a generated password.
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/health")
                        .permitAll()
                        // Swagger is only enabled locally (SWAGGER_ENABLED); it is off in the cloud.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()

                        // Categories and contents (RF-10, RF-11, RF-12): read by the community and ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/training/categories",
                                "/api/v1/training/contents", "/api/v1/training/contents/*")
                        .hasAnyAuthority(Permissions.CONTENT_READ, Permissions.CONTENT_MANAGE)
                        // Content views (RF-13): community only
                        .requestMatchers(HttpMethod.POST, "/api/v1/training/contents/*/views")
                        .hasAuthority(Permissions.CONTENT_READ)

                        // Evaluations (RF-14)
                        .requestMatchers(HttpMethod.GET, "/api/v1/training/evaluations",
                                "/api/v1/training/evaluations/*")
                        .hasAnyAuthority(Permissions.QUIZ_TAKE, Permissions.CONTENT_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/training/evaluations/*/attempts")
                        .hasAuthority(Permissions.QUIZ_TAKE)

                        // Progress (RF-13)
                        .requestMatchers(HttpMethod.GET, "/api/v1/training/progress/me")
                        .hasAuthority(Permissions.QUIZ_TAKE)

                        // Tutor (RF-15)
                        .requestMatchers(HttpMethod.POST, "/api/v1/tutor/questions")
                        .hasAuthority(Permissions.TUTOR_USE)

                        // Everything else under /training is management: ADMIN only.
                        // It goes last so it does not hide the rules above.
                        .requestMatchers("/api/v1/training/**")
                        .hasAuthority(Permissions.CONTENT_MANAGE)

                        // Any undeclared route is denied. New endpoints must be declared above.
                        .anyRequest().denyAll());

        return http.build();
    }
}
