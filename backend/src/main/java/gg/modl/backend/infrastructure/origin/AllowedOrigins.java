package gg.modl.backend.infrastructure.origin;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared browser-origin policy for HTTP, request forgery checks, and realtime. */
@Component
public final class AllowedOrigins {
    private final boolean reflect;
    private final Set<String> origins;

    public AllowedOrigins(@Value("${CORS_ALLOWED_ORIGINS:http://localhost:8080}") String setting) {
        String value = setting == null ? "" : setting.trim();
        reflect = value.equals("*") || value.equalsIgnoreCase("dynamic");
        if (reflect) {
            origins = Set.of();
        } else {
            if (value.isEmpty()) throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must contain origins, * or dynamic");
            origins = Arrays.stream(value.split(",", -1)).map(String::trim).map(origin -> {
                String normalized = normalize(origin);
                if (normalized == null) throw new IllegalArgumentException("Invalid origin in CORS_ALLOWED_ORIGINS: " + origin);
                return normalized;
            }).collect(Collectors.toUnmodifiableSet());
        }
    }

    public boolean allows(String origin) {
        String normalized = normalize(origin);
        return normalized != null && (reflect || origins.contains(normalized));
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
