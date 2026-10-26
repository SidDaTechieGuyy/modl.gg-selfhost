package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServerHeaderFilter extends OncePerRequestFilter {
    private final ServerService serverService;
    private final AllowedOrigins allowedOrigins;

    public ServerHeaderFilter(ServerService serverService, AllowedOrigins allowedOrigins) {
        this.serverService = serverService;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || request.getRequestURI().startsWith("/v1/public/registration");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        // Host and forwarded headers never select a different instance.
        Server server = serverService.getInstance();
        if (server != null) {
            request.setAttribute(RequestAttribute.SERVER, server);
        } else if (!request.getRequestURI().equals("/v1/panel/auth/login")) {
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Instance setup is required. Visit /register.\"}");
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.allows(origin)) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Origin is not allowed.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
