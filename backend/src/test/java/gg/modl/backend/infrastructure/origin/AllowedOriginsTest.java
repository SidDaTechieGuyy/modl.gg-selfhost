package gg.modl.backend.infrastructure.origin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedOriginsTest {
    @ParameterizedTest
    @ValueSource(strings = {"*", "dynamic"})
    void reflectsAnyValidBrowserOrigin(String setting) {
        AllowedOrigins policy = new AllowedOrigins(setting);
        assertTrue(policy.allows("https://moderation.duckdns.org"));
        assertTrue(policy.allows("http://localhost:8123"));
        assertFalse(policy.allows("null"));
        assertFalse(policy.allows(null));
        assertFalse(policy.allows("https://example.org/path"));
        assertFalse(policy.allows("https://user@example.org"));
        assertFalse(policy.allows("file:///etc/passwd"));
    }

    @Test
    void exactListChecksSchemeHostAndPort() {
        AllowedOrigins policy = new AllowedOrigins(" https://panel.example.org, http://localhost:8123 ");
        assertTrue(policy.allows("https://PANEL.example.org:443"));
        assertTrue(policy.allows("http://localhost:8123"));
        assertFalse(policy.allows("http://panel.example.org"));
        assertFalse(policy.allows("https://panel.example.org.evil.org"));
        assertFalse(policy.allows("http://localhost:8124"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://example.org,", "*,https://example.org", "null", "https://example.org/", "example.org"})
    void rejectsInvalidSettingsAtStartup(String setting) {
        assertThrows(IllegalArgumentException.class, () -> new AllowedOrigins(setting));
    }
}
