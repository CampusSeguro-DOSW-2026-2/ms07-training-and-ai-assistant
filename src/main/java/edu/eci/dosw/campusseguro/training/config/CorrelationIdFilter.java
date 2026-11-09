package edu.eci.dosw.campusseguro.training.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Reads X-Correlation-ID (or generates one), echoes it in the response and puts it in the log context.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = normalize(request.getHeader(HEADER));
        request.setAttribute(HEADER, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String from(HttpServletRequest request) {
        Object id = request.getAttribute(HEADER);
        return id != null ? id.toString() : normalize(request.getHeader(HEADER));
    }

    // Keeps the received id only if it is a valid UUID; otherwise generates a new one.
    private static String normalize(String received) {
        if (received != null) {
            try {
                return UUID.fromString(received.trim()).toString();
            } catch (IllegalArgumentException e) {
                return UUID.randomUUID().toString();
            }
        }
        return UUID.randomUUID().toString();
    }
}
