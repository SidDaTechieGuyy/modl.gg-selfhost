package gg.modl.backend.infrastructure.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class ServerHeaderFilterTest {
    @Test
    void everyHostUsesTheSameInstance() throws Exception {
        var servers = mock(ServerService.class);
        var server = new Server("Test", "modl_instance", "owner@example.com");
        when(servers.getInstance()).thenReturn(server);
        var filter = new ServerHeaderFilter(servers, new AllowedOrigins("https://panel.duckdns.org"));
        for (String host : new String[]{"panel.duckdns.org", "localhost", "different.example"}) {
            var request = new MockHttpServletRequest("GET", "/v1/panel/auth/me");
            request.setServerName(host);
            request.addHeader("X-Server-Domain", "untrusted.example");
            request.addHeader("Origin", "https://panel.duckdns.org");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertSame(server, request.getAttribute(RequestAttribute.SERVER));
            assertEquals(200, response.getStatus());
        }
    }
    @Test
    void rejectsUnlistedOrigin() throws Exception {
        var servers = mock(ServerService.class);
        when(servers.getInstance()).thenReturn(new Server("Test", "modl_instance", "owner@example.com"));
        var request = new MockHttpServletRequest("GET", "/v1/panel/auth/me");
        request.addHeader("Origin", "https://evil.example");
        var response = new MockHttpServletResponse();
        new ServerHeaderFilter(servers, new AllowedOrigins("https://allowed.example")).doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }
}
