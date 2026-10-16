package com.nova.tech.bank.sphere.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

class RefreshCookieTest {

    @Test
    void set_carriesEverySecurityFlag() {
        String h = new RefreshCookie(true, 7, false).set("abc").getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(h.startsWith("bs_refresh=abc"));
        assertTrue(h.contains("HttpOnly"));
        assertTrue(h.contains("Secure"));
        assertTrue(h.contains("SameSite=Strict"));
        assertTrue(h.contains("Path=/api/auth"));
        assertTrue(h.contains("Max-Age=604800"));
    }

    @Test
    void secureFlagFollowsConfiguration() {
        String h = new RefreshCookie(false, 7, false).set("abc").getFirst(HttpHeaders.SET_COOKIE);
        assertFalse(h.contains("Secure"));
    }

    @Test
    void clear_expiresTheCookieImmediately() {
        String h = new RefreshCookie(true, 7, false).clear().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(h.startsWith("bs_refresh=;"));
        assertTrue(h.contains("Max-Age=0"));
    }
}

