package gg.modl.backend.registration;

import gg.modl.backend.auth.PasswordAuthService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/public/registration")
@RequiredArgsConstructor
public class PublicRegistrationController {
    private final PasswordAuthService passwordAuthService;
    private final SessionService sessions;
    private final CookieUtil cookies;

    public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 3, max = 100) String serverName) {}

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("registrationOpen", passwordAuthService.registrationOpen());
    }

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest body,
                                     HttpServletRequest request, HttpServletResponse response) {
        Server server = passwordAuthService.register(body.username(), body.username(), body.password(), body.serverName());
        AuthSessionData session = sessions.createSession(server, server.getAdminEmail(),
            RequestUtil.getClientIp(request), request.getHeader("User-Agent"));
        response.addCookie(cookies.createSessionCookie(session.getId()));
        return ResponseEntity.status(201).body(Map.of("success", true));
    }
}
