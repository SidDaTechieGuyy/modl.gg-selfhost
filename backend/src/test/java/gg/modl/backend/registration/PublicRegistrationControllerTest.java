package gg.modl.backend.registration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import gg.modl.backend.auth.PasswordAuthService;
import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.session.*;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.backend.server.data.Server;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class PublicRegistrationControllerTest {
    @Test
    void registrationCreatesAnAuthenticatedSession() {
        var passwords = mock(PasswordAuthService.class);
        var sessions = mock(SessionService.class);
        var config = new AuthConfiguration();
        config.setCookieSecure(false);
        var controller = new PublicRegistrationController(passwords, sessions, new CookieUtil(config));
        var server = new Server("Test", "modl_instance", "owner@example.com");
        when(passwords.register("owner", "owner@example.com", "a-long-password!", "Test")).thenReturn(server);
        var session = new AuthSessionData(); session.setId("new-random-session");
        when(sessions.createSession(eq(server), eq("owner@example.com"), any(), any())).thenReturn(session);
        var response = new MockHttpServletResponse();
        var result = controller.register(new PublicRegistrationController.RegistrationRequest("owner", "owner@example.com", "a-long-password!", "Test"),
            new MockHttpServletRequest("POST", "/v1/public/registration"), response);
        assertEquals(201, result.getStatusCode().value());
        assertEquals("new-random-session", response.getCookie("MODL_SESSION").getValue());
        assertTrue(response.getCookie("MODL_SESSION").isHttpOnly());
    }
    @Test
    void statusClosesAfterOwnerCreation() {
        var passwords = mock(PasswordAuthService.class);
        var controller = new PublicRegistrationController(passwords, mock(SessionService.class), new CookieUtil(new AuthConfiguration()));
        when(passwords.registrationOpen()).thenReturn(true, false);
        assertEquals(true, controller.status().get("registrationOpen"));
        assertEquals(false, controller.status().get("registrationOpen"));
    }
}
