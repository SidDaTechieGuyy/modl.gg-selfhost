package gg.modl.backend.infrastructure.origin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedOriginsTest {
    @Test void trustsOnlyTheConfiguredBaseUrl() {
        var policy = new AllowedOrigins("https://moderation.duckdns.org/");
        assertTrue(policy.allows("https://MODERATION.duckdns.org:443"));
        assertFalse(policy.allows("https://other.duckdns.org"));
        assertFalse(policy.allows("http://moderation.duckdns.org"));
        assertFalse(policy.allows("https://moderation.duckdns.org:8443"));
        assertFalse(policy.allows("https://moderation.duckdns.org.evil.org"));
    }
    @Test void supportsLocalAndIpv6Origins() {
        assertTrue(new AllowedOrigins("http://localhost:8080").allows("http://localhost:8080"));
        assertFalse(new AllowedOrigins("http://localhost:8080").allows("http://127.0.0.1:8080"));
        assertTrue(new AllowedOrigins("http://[::1]:8080").allows("http://[::1]:8080"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "*", "dynamic", "https://example.org,https://other.org", "null", "https://example.org/path", "https://user@example.org", "https://example.org?x=1", "https://example.org#x", "example.org"})
    void rejectsInvalidBaseUrlsAtStartup(String value) {
        assertThrows(IllegalArgumentException.class, () -> new AllowedOrigins(value));
    }
    @ParameterizedTest
    @ValueSource(strings = {"null", "https://example.org/path", "https://user@example.org", "file:///tmp/file", "https://example.org/"})
    void rejectsMalformedRequestOrigins(String value) {
        assertFalse(new AllowedOrigins("https://example.org").allows(value));
    }
}
