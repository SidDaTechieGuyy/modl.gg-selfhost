package com.modl.selfhost.service;

import com.modl.selfhost.auth.JwtService;
import com.modl.selfhost.auth.PasswordService;
import com.modl.selfhost.model.User;
import com.modl.selfhost.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    public boolean ownerExists() {
        return userRepository.count() > 0;
    }

    public AuthResult register(String username, String email, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        String cleanUsername = username.trim();
        String cleanEmail = email.trim().toLowerCase();

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new IllegalArgumentException("A user with that username already exists.");
        }
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("A user with that email already exists.");
        }
        if (ownerExists()) {
            throw new IllegalStateException("Registration is closed after the owner exists.");
        }

        String passwordHash = passwordService.hash(password);
        User user = new User(cleanUsername, cleanEmail, passwordHash, "owner");
        User saved = userRepository.save(user);
        String token = jwtService.createToken(saved.getId());
        return new AuthResult(token, saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRole());
    }

    public AuthResult login(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("Username or email is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        Optional<User> userOpt = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail.trim().toLowerCase()));
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!passwordService.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        String token = jwtService.createToken(user.getId());
        return new AuthResult(token, user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public Optional<User> getById(String userId) {
        return userRepository.findById(userId);
    }

    public static record AuthResult(String token, String id, String username, String email, String role) {}
}
