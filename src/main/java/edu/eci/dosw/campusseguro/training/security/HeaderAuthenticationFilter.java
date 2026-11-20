package edu.eci.dosw.campusseguro.training.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ESTUDIANTE", Set.of(
                    Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE),
            "PROFESOR", Set.of(
                    Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE),
            "ADMINISTRATIVO", Set.of(
                    Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE),
            "BRIGADISTA", Set.of(
                    Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE),
            "COORDINADOR", Set.of(
                    Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE),
            "ORGANIZADOR", Set.of(
                    Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE),
            "ADMIN", Set.of(Permissions.CONTENT_MANAGE),
            "AUDITOR", Set.of()
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String userId = request.getHeader(USER_ID);
        String roleHeader = request.getHeader(USER_ROLE);

        if (hasText(userId) && hasText(roleHeader)) {
            Set<String> permissions = Arrays.stream(roleHeader.split(","))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(role -> ROLE_PERMISSIONS.getOrDefault(role, Set.of()))
                    .flatMap(Set::stream)
                    .collect(java.util.stream.Collectors.toSet());

            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .sorted()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(userId.trim(), null, authorities));
            SecurityContextHolder.setContext(context);
        }

        chain.doFilter(request, response);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
