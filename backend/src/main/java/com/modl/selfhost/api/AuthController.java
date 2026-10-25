package com.modl.selfhost.api;

import com.modl.selfhost.config.AppProperties;
import com.modl.selfhost.config.OriginPolicy;
import com.modl.selfhost.model.User;
import com.modl.selfhost.model.UploadRecord;
import com.modl.selfhost.repository.UserRepository;
import com.modl.selfhost.service.AuthService;
import com.modl.selfhost.service.UploadService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AppProperties appProperties;

    public AuthController(AuthService authService, UserRepository userRepository, JwtService jwtService, AppProperties appProperties) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
    }

    @GetMapping("/auth/owner-status")
    public Map<String, Boolean> ownerStatus() {
        return Map.of("ownerExists", authService.ownerExists());
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload, HttpServletResponse response) {
        try {
            String username = payload.get("username");
            String email = payload.get("email");
            String password = payload.get("password");
            AuthService.AuthResult result = authService.register(username, email, password);
            setAuthCookie(response, result.token());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "token", result.token(),
                    "user", Map.of("id", result.id(), "username", result.username(), "email", result.email(), "role", result.role())
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload, HttpServletResponse response) {
        try {
            String usernameOrEmail = payload.get("usernameOrEmail");
            String password = payload.get("password");
            AuthService.AuthResult result = authService.login(usernameOrEmail, password);
            setAuthCookie(response, result.token());
            return ResponseEntity.ok(Map.of(
                    "token", result.token(),
                    "user", Map.of("id", result.id(), "username", result.username(), "email", result.email(), "role", result.role())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Boolean>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("auth_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required."));
        }
        try {
            String userId = jwtService.parseSubject(token);
            Optional<User> user = userRepository.findById(userId);
            if (user.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token."));
            }
            return ResponseEntity.ok(Map.of("user", Map.of(
                    "id", user.get().getId(),
                    "username", user.get().getUsername(),
                    "email", user.get().getEmail(),
                    "role", user.get().getRole()
            )));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired token."));
        }
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("auth_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("auth_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
