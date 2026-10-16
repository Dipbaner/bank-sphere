package com.nova.tech.bank.sphere.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler{

    @Value("${app.oauth2.failure-redirect}")
    private String failureRedirect;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        // Cancelled consent, bad state, Google outage... the browser only ever sees one generic code.
        response.sendRedirect(failureRedirect + "?error=oauth_failed");
    }
}
