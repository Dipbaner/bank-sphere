package com.nova.tech.bank.sphere.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, String path, boolean prefix, int capacity, Duration window) {}

    private static final List<Rule> RULES = List.of(
            new Rule("login",    "/api/auth/login",               false, 10, Duration.ofMinutes(1)),
            new Rule("register", "/api/auth/register",            false, 10, Duration.ofHours(1)),
            new Rule("forgot",   "/api/auth/forgot-password",     false,  5, Duration.ofMinutes(10)),
            new Rule("resend",   "/api/auth/resend-verification", false,  5, Duration.ofMinutes(10)),
            new Rule("reset",    "/api/auth/reset-password",      false, 10, Duration.ofMinutes(10)),
            new Rule("otp",      "/api/auth/otp/",                true,  15, Duration.ofMinutes(1)),
            new Rule("stepup",   "/api/auth/step-up",             false, 15, Duration.ofMinutes(1)),
            new Rule("refresh",  "/api/auth/refresh",             false, 30, Duration.ofMinutes(1)));

    private final RateLimiter limiter;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter limiter, @Value("${app.ratelimit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (enabled && "POST".equals(request.getMethod())) {
            Rule rule = match(request.getRequestURI());
            if (rule != null) {
                // getRemoteAddr is the real connecting address. Never trust X-Forwarded-For
                // unless the app sits behind a proxy that you control.
                long wait = limiter.tryConsume(rule.name() + "|" + request.getRemoteAddr(),
                        rule.capacity(), rule.window());
                if (wait > 0) {
                    response.setStatus(429);
                    response.setHeader("Retry-After", String.valueOf(wait));
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                            + "\",\"status\":429,\"code\":\"TOO_MANY_REQUESTS\","
                            + "\"message\":\"Too many attempts. Try again in " + wait + " seconds.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static Rule match(String uri) {
        for (Rule r : RULES) {
            if (r.prefix() ? uri.startsWith(r.path()) : uri.equals(r.path())) return r;
        }
        return null;
    }
}