package gg.modl.backend.realtime.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import gg.modl.backend.realtime.auth.RealtimeOriginValidator;
import java.util.HashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class OriginHandshakeInterceptorTest {
    @ParameterizedTest
    @ValueSource(strings = {"*", "dynamic", "https://panel.duckdns.org,https://other.example"})
    void handshakeAndPanelAuthenticationUseTheSamePolicy(String setting) {
        var policy = new AllowedOrigins(setting);
        var request = mock(ServerHttpRequest.class);
        var headers = new HttpHeaders();
        headers.setOrigin("https://panel.duckdns.org");
        when(request.getHeaders()).thenReturn(headers);
        var interceptor = new OriginHandshakeInterceptor(policy);
        assertTrue(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()));
        assertTrue(new RealtimeOriginValidator(policy).isAllowedPanelOrigin(headers.getOrigin(), "instance"));
        headers.setOrigin("null");
        assertFalse(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()));
        assertFalse(new RealtimeOriginValidator(policy).isAllowedPanelOrigin("null", "instance"));
        headers.remove(HttpHeaders.ORIGIN);
        assertTrue(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()));
        assertFalse(new RealtimeOriginValidator(policy).isAllowedPanelOrigin(null, "instance"));
    }
}
