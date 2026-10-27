package gg.modl.backend.infrastructure.cors;

import static org.junit.jupiter.api.Assertions.*;
import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

class DynamicCorsConfigurationSourceTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://panel.duckdns.org", "https://panel.duckdns.org:443", "https://panel.duckdns.org/"})
    void credentialedPreflightReflectsThePermittedRequestOrigin(String setting) throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/v1/panel/auth/login");
        request.addHeader("Origin", "https://panel.duckdns.org");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type");
        var response = new MockHttpServletResponse();
        var source = new DynamicCorsConfigurationSource(new AllowedOrigins(setting));
        assertTrue(new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response));
        assertEquals("https://panel.duckdns.org", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeaders("Vary").stream().anyMatch(value -> value.contains("Origin")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "null", "https://allowed.example/path"})
    void refusesUnlistedAndMalformedOrigins(String origin) throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/v1/panel/auth/login");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        var response = new MockHttpServletResponse();
        var source = new DynamicCorsConfigurationSource(new AllowedOrigins("https://allowed.example"));
        assertFalse(new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response));
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
}
