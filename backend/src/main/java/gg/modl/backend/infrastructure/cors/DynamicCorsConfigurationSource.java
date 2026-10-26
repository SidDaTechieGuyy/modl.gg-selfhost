package gg.modl.backend.infrastructure.cors;

import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Component
@RequiredArgsConstructor
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {
    private final AllowedOrigins allowedOrigins;
    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();
        String origin = request.getHeader("Origin");
        if (allowedOrigins.allows(origin)) config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-RateLimit-Remaining", "X-RateLimit-Retry-After",
            "X-Rate-Limit-Remaining", "X-Rate-Limit-Retry-After-Seconds"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
    public void invalidateCache(String domain) {}
    public void invalidateCacheForOrigin(String origin) {}
}
