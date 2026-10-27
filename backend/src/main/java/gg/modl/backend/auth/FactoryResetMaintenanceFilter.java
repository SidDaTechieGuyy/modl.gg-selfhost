package gg.modl.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class FactoryResetMaintenanceFilter extends OncePerRequestFilter {
    private final FactoryResetService reset;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (reset.requested()) {
            response.setStatus(503);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "5");
            response.getWriter().write("{\"message\":\"Factory reset in progress. Please wait for the backend to restart.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
