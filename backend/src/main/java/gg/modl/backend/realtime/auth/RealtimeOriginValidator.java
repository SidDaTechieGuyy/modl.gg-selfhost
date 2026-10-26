package gg.modl.backend.realtime.auth;

import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeOriginValidator {
    private final AllowedOrigins allowedOrigins;
    public boolean isAllowedPanelOrigin(String origin, String ignoredScope) {
        return allowedOrigins.allows(origin);
    }
}
