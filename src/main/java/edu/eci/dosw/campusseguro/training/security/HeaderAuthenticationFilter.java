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

// Reads the headers set by the Gateway (X-User-Id, X-User-Role and X-User-Permission) and builds
// the user identity. The authorities are the permissions in X-User-Permission (comma separated):
// roles are data owned by MS01, so MS07 never maps a role name to permissions.
// If any of the three headers is missing the request stays anonymous and the chain answers 401.
// An empty X-User-Permission is valid: the user is authenticated with no permissions (403).
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String USER_PERMISSION = "X-User-Permission";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID);
        String role = request.getHeader(USER_ROLE);
        String permissionHeader = request.getHeader(USER_PERMISSION);

        if (hasText(userId) && hasText(role) && permissionHeader != null) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(permissionHeader.split(","))
                    .map(String::trim)
                    .filter(permission -> !permission.isEmpty())
                    .distinct()
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
