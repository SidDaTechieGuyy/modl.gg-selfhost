package gg.modl.backend.realtime.config;

import gg.modl.backend.infrastructure.origin.AllowedOrigins;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
public class OriginHandshakeInterceptor implements HandshakeInterceptor {
    private final AllowedOrigins allowedOrigins;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String origin = request.getHeaders().getOrigin();
        // Native plugin clients authenticate with their API key after the handshake.
        if (origin == null || allowedOrigins.allows(origin)) return true;
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception error) {}
}
