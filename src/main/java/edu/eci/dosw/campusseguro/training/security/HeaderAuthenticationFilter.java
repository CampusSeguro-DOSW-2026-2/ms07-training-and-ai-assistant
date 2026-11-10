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
import java.util.Locale;

// Reads the headers set by the Gateway (X-User-Id and X-User-Role) and builds the user identity.
// If either is missing the request stays anonymous and the chain answers 401.
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID);
        String roleHeader = request.getHeader(USER_ROLE);

        if (userId != null && !userId.isBlank() && roleHeader != null && !roleHeader.isBlank()) {
            List<String> roles = Arrays.stream(roleHeader.split(","))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(role -> role.toUpperCase(Locale.ROOT))
                    .toList();
            List<SimpleGrantedAuthority> authorities = RolePermissions.permissionsOf(roles).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(userId.trim(), null, authorities));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
