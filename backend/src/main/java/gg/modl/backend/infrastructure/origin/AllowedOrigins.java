package gg.modl.backend.infrastructure.origin;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared browser-origin policy for HTTP, request forgery checks, and realtime. */
@Component
public final class AllowedOrigins {
    private final String origin;

    public AllowedOrigins(@Value("${BASE_URL:http://localhost:8080}") String baseUrl) {
        origin = normalize(baseUrl == null ? null : baseUrl.replaceAll("/+$", ""));
        if (origin == null) throw new IllegalArgumentException("BASE_URL must be an HTTP(S) origin without a path, credentials, query or fragment");
    }

    public boolean allows(String origin) {
        String normalized = normalize(origin);
        return normalized != null && this.origin.equals(normalized);
    }

    private static String normalize(String origin) {
        if (origin == null || origin.isBlank() || !origin.equals(origin.trim())) return null;
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getPort() > 65535 || uri.getPort() == 0) return null;
            int port = uri.getPort();
            if ((scheme.equalsIgnoreCase("https") && port == 443)
                || (scheme.equalsIgnoreCase("http") && port == 80)) port = -1;
            return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException e) { return null; }
    }
}
