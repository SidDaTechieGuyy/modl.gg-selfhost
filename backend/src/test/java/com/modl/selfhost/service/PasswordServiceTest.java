package com.modl.selfhost.service;

import com.modl.selfhost.auth.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    @Test
    void hashesPasswordsAndMatchesThem() {
        PasswordService service = new PasswordService(new BCryptPasswordEncoder());
        String hash = service.hash("super-secret");
        assertNotEquals("super-secret", hash);
        assertTrue(service.matches("super-secret", hash));
        assertFalse(service.matches("wrong-pass", hash));
    }
}
