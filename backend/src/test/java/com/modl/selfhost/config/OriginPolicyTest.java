package com.modl.selfhost.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OriginPolicyTest {
    @Test
    void wildcardAllowsAnyOrigin() {
        assertTrue(OriginPolicy.isAllowed("https://example.com", "*"));
    }

    @Test
    void commaSeparatedOriginsWork() {
        assertTrue(OriginPolicy.isAllowed("https://example.com", "https://example.com,https://api.example.com"));
        assertFalse(OriginPolicy.isAllowed("https://evil.test", "https://example.com,https://api.example.com"));
    }

    @Test
    void dynamicMatchingMatchesExactDomain() {
        assertTrue(OriginPolicy.isAllowed("https://duckdns.example.com", "https://*.example.com"));
    }
}
