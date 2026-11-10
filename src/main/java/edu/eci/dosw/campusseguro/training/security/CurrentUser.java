package edu.eci.dosw.campusseguro.training.security;

import edu.eci.dosw.campusseguro.training.exception.BadRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

// Access to the user set by HeaderAuthenticationFilter from the Gateway headers.
public final class CurrentUser {

    private CurrentUser() {
    }

    public static boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authenticated user in the security context.");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HeaderAuthenticationFilter.USER_ID, "El identificador de usuario no es válido.");
        }
    }
}
