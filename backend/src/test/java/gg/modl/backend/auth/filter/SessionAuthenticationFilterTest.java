package gg.modl.backend.auth.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.infrastructure.filter.SessionAuthenticationFilter;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.Cookie;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshesSessionCookieAsHostOnlyForHttpsOrigins() throws Exception {
        SessionService sessionService = mock(SessionService.class);

        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setDevelopmentMode(false);
        authConfiguration.setCookieSecure(true);
        authConfiguration.setSessionCookieName("MODL_SESSION");

        CookieUtil cookieUtil = new CookieUtil(authConfiguration);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(sessionService, authConfiguration, cookieUtil);

        Server server = new Server("Alpha", "server_alpha", "admin@example.com");
        AuthSessionData session = new AuthSessionData("token-123", "staff@example.com", new Date(), new Date(System.currentTimeMillis() + 1000), null, null);

        when(sessionService.findAndRefreshSession(server, "token-123")).thenReturn(Optional.of(session));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/panel/auth/me");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setCookies(new Cookie("MODL_SESSION", "token-123"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Cookie refreshedCookie = response.getCookie("MODL_SESSION");
        assertNotNull(refreshedCookie);
        assertEquals("token-123", refreshedCookie.getValue());
        assertEquals((int) AuthConfiguration.MIN_SESSION_DURATION_SECONDS, refreshedCookie.getMaxAge());
        assertNull(refreshedCookie.getDomain());
        assertEquals("None", refreshedCookie.getAttribute("SameSite"));

        verify(sessionService).findAndRefreshSession(server, "token-123");
    }

    @Test
    void refreshesSessionCookieWithLaxSameSiteOnLocalHttp() throws Exception {
        SessionService sessionService = mock(SessionService.class);

        AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setBaseUrl("http://localhost:8080");
        authConfiguration.setCookieSecure(true);
        authConfiguration.setSessionCookieName("MODL_SESSION");

        CookieUtil cookieUtil = new CookieUtil(authConfiguration);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(sessionService, authConfiguration, cookieUtil);

        Server server = new Server("Custom", "server_custom", "admin@example.com");
        AuthSessionData session = new AuthSessionData("token-456", "staff@example.com", new Date(), new Date(System.currentTimeMillis() + 1000), null, null);

        when(sessionService.findAndRefreshSession(server, "token-456")).thenReturn(Optional.of(session));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/panel/auth/me");
        request.setAttribute(RequestAttribute.SERVER, server);
        request.setCookies(new Cookie("MODL_SESSION", "token-456"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Cookie refreshedCookie = response.getCookie("MODL_SESSION");
        assertNotNull(refreshedCookie);
        assertEquals("token-456", refreshedCookie.getValue());
        assertNull(refreshedCookie.getDomain());
        assertEquals("Lax", refreshedCookie.getAttribute("SameSite"));

        verify(sessionService).findAndRefreshSession(server, "token-456");
    }
}
