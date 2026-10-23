package com.nova.tech.bank.sphere.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private RateLimitFilter filter(boolean enabled) {
        return new RateLimitFilter(new RateLimiter(Clock.systemUTC()), enabled);
    }

    private MockHttpServletResponse call(RateLimitFilter f, String method, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void eleventhLoginInAMinute_isRefusedWithRetryAfter() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 0; i < 10; i++) assertEquals(200, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());

        MockHttpServletResponse res = call(f, "POST", "/api/auth/login", "1.1.1.1");
        assertEquals(429, res.getStatus());
        assertNotNull(res.getHeader("Retry-After"));
        assertTrue(res.getContentAsString().contains("TOO_MANY_REQUESTS"));
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 0; i < 11; i++) call(f, "POST", "/api/auth/login", "1.1.1.1");
        assertEquals(200, call(f, "POST", "/api/auth/login", "2.2.2.2").getStatus());
    }

    @Test
    void otpEndpointsShareOneBucket() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 0; i < 15; i++) call(f, "POST", i % 2 == 0 ? "/api/auth/otp/verify" : "/api/auth/otp/backup", "1.1.1.1");
        assertEquals(429, call(f, "POST", "/api/auth/otp/verify", "1.1.1.1").getStatus());
    }

    @Test
    void getRequestsAndUnlistedPathsAreNeverLimited() throws Exception {
        RateLimitFilter f = filter(true);
        for (int i = 0; i < 50; i++) {
            assertEquals(200, call(f, "GET", "/api/auth/login", "1.1.1.1").getStatus());
            assertEquals(200, call(f, "POST", "/api/auth/logout", "1.1.1.1").getStatus());
        }
    }

    @Test
    void disabledFilterNeverLimits() throws Exception {
        RateLimitFilter f = filter(false);
        for (int i = 0; i < 50; i++) assertEquals(200, call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus());
    }
}