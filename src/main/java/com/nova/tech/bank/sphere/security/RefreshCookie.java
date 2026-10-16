package com.nova.tech.bank.sphere.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookie {

    public static final String NAME = "bs_refresh";

    private final boolean secure;
    private final long refreshDays;
    private final boolean refreshInBody;

    public RefreshCookie(@Value("${app.cookie.secure}") boolean secure,
                         @Value("${app.jwt.refresh-days}") long refreshDays,
                         @Value("${app.auth.refresh-in-body}") boolean refreshInBody) {
        this.secure = secure;
        this.refreshDays = refreshDays;
        this.refreshInBody = refreshInBody;
    }

    public HttpHeaders set(String rawToken) {
        return header(rawToken, Duration.ofDays(refreshDays));
    }

    public HttpHeaders clear() {
        return header("", Duration.ZERO);
    }

    public boolean exposeInBody() {
        return refreshInBody;
    }

    private HttpHeaders header(String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)              // JavaScript cannot read it
                .secure(secure)              // HTTPS only in production
                .sameSite("Strict")          // never sent from other sites
                .path("/api/auth")           // only sent to the auth endpoints
                .maxAge(maxAge)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        return headers;
    }
}