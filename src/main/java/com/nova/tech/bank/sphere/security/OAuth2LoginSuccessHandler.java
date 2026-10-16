package com.nova.tech.bank.sphere.security;

import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.exception.OAuthLoginException;
import com.nova.tech.bank.sphere.service.GoogleLoginService;
import com.nova.tech.bank.sphere.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements
AuthenticationSuccessHandler
{

    private final GoogleLoginService googleLoginService;
    private final JwtService jwtService;
    private final OtpService otpService;

    @Value("${app.oauth2.success-redirect}")
    private String successRedirect;

    @Value("${app.oauth2.failure-redirect}")
    private String failureRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // The Google handshake is finished. Destroy its session so that from now on
        // ONLY our JWTs can grant access.
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        try {
            if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
                throw new OAuthLoginException("oauth_failed");
            }
            User user = googleLoginService.loginOrRegister(oidc);

            // The token travels in the URL FRAGMENT (after '#'), which browsers never send to servers.
            // It is a 5-minute pre-auth token: it can reach only the OTP endpoints.
            boolean enrollmentRequired = !otpService.isEnabled(user.getId());
            response.sendRedirect(successRedirect
                    + "#preAuthToken=" + jwtService.generatePreAuthToken(user)
                    + "&enrollmentRequired=" + enrollmentRequired);

        } catch (OAuthLoginException e) {
            response.sendRedirect(failureRedirect + "?error=" + e.getCode());
        }
    }
}

