package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.OAuthLoginException;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleLoginServiceTest {

    @Mock UserRepository userRepository;
    @Mock VerificationTokenRepository tokenRepository;
    @Mock LockoutService lockoutService;
    @Mock AuditService auditService;
    @Mock OidcUser oidc;
    @InjectMocks GoogleLoginService service;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(10L);
            return u;
        });
        lenient().when(userRepository.findByGoogleSubject(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    }

    private void google(String sub, String email, Boolean verified, String name) {
        lenient().when(oidc.getSubject()).thenReturn(sub);
        lenient().when(oidc.getEmail()).thenReturn(email);
        lenient().when(oidc.getEmailVerified()).thenReturn(verified);
        lenient().when(oidc.getFullName()).thenReturn(name);
    }

    private User localUser(UserStatus status) {
        User u = new User();
        u.setId(5L);
        u.setEmail("dip@gmail.com");
        u.setName("Dip");
        u.setStatus(status);
        u.setProvider(AuthProvider.LOCAL);
        u.setPasswordHash("$2a$12$somehash");
        u.setEmailVerified(status == UserStatus.ACTIVE);
        return u;
    }

    @Test
    void newGoogleUser_isCreatedAsActiveCustomer_withoutPasswordOrPhone() {
        google("sub-1", "Dip@Gmail.com", true, "Dip Banerjee");

        User u = service.loginOrRegister(oidc);

        assertEquals("dip@gmail.com", u.getEmail());
        assertEquals(Role.CUSTOMER, u.getRole());
        assertEquals(UserStatus.ACTIVE, u.getStatus());
        assertEquals(AuthProvider.GOOGLE, u.getProvider());
        assertNull(u.getPasswordHash());
        assertNull(u.getPhone());
        assertTrue(u.isEmailVerified());
        assertEquals("sub-1", u.getGoogleSubject());
    }

    @Test
    void unverifiedGoogleEmail_isRejected_andNothingIsSaved() {
        google("sub-1", "dip@gmail.com", false, "Dip");

        OAuthLoginException ex = assertThrows(OAuthLoginException.class, () -> service.loginOrRegister(oidc));

        assertEquals("email_not_verified", ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void missingEmailVerifiedClaim_isRejected() {
        google("sub-1", "dip@gmail.com", null, "Dip");
        assertThrows(OAuthLoginException.class, () -> service.loginOrRegister(oidc));
    }

    @Test
    void knownGoogleSubject_logsInWithoutCreatingAnything() {
        User existing = localUser(UserStatus.ACTIVE);
        existing.setGoogleSubject("sub-1");
        google("sub-1", "dip@gmail.com", true, "Dip");
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.of(existing));

        assertSame(existing, service.loginOrRegister(oidc));
        verify(userRepository, never()).save(any());
    }

    @Test
    void activeLocalAccount_isLinked_andKeepsItsPassword() {
        User existing = localUser(UserStatus.ACTIVE);
        google("sub-1", "dip@gmail.com", true, "Dip");
        when(userRepository.findByEmail("dip@gmail.com")).thenReturn(Optional.of(existing));

        User u = service.loginOrRegister(oidc);

        assertEquals("sub-1", u.getGoogleSubject());
        assertEquals("$2a$12$somehash", u.getPasswordHash());
        assertEquals(AuthProvider.LOCAL, u.getProvider());
    }

    @Test
    void pendingLocalAccount_losesItsPassword_andBecomesActive() {
        User pending = localUser(UserStatus.PENDING_EMAIL_VERIFICATION);
        google("sub-1", "dip@gmail.com", true, "Dip");
        when(userRepository.findByEmail("dip@gmail.com")).thenReturn(Optional.of(pending));

        User u = service.loginOrRegister(oidc);

        assertNull(u.getPasswordHash());                       // pre-hijack protection
        assertEquals(UserStatus.ACTIVE, u.getStatus());
        assertTrue(u.isEmailVerified());
        assertEquals(AuthProvider.GOOGLE, u.getProvider());
        verify(tokenRepository).deleteByUserAndType(pending, TokenType.EMAIL_VERIFY);
    }

    @Test
    void disabledAccount_isNotLinked() {
        User disabled = localUser(UserStatus.DISABLED);
        google("sub-1", "dip@gmail.com", true, "Dip");
        when(userRepository.findByEmail("dip@gmail.com")).thenReturn(Optional.of(disabled));

        OAuthLoginException ex = assertThrows(OAuthLoginException.class, () -> service.loginOrRegister(oidc));

        assertEquals("account_blocked", ex.getCode());
        assertNull(disabled.getGoogleSubject());
    }

    @Test
    void emailLinkedToAnotherGoogleAccount_isRefused() {
        User existing = localUser(UserStatus.ACTIVE);
        existing.setGoogleSubject("other-sub");
        google("sub-1", "dip@gmail.com", true, "Dip");
        when(userRepository.findByEmail("dip@gmail.com")).thenReturn(Optional.of(existing));

        OAuthLoginException ex = assertThrows(OAuthLoginException.class, () -> service.loginOrRegister(oidc));

        assertEquals("account_conflict", ex.getCode());
        assertEquals("other-sub", existing.getGoogleSubject());
    }

    @Test
    void temporarilyLockedAccount_isRefused() {
        User existing = localUser(UserStatus.ACTIVE);
        existing.setGoogleSubject("sub-1");
        google("sub-1", "dip@gmail.com", true, "Dip");
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.of(existing));
        when(lockoutService.isLocked(existing)).thenReturn(true);

        OAuthLoginException ex = assertThrows(OAuthLoginException.class, () -> service.loginOrRegister(oidc));
        assertEquals("account_locked", ex.getCode());
    }

    @Test
    void longOrMissingNames_areCleaned() {
        google("sub-1", "a@gmail.com", true, null);
        assertEquals("Google User", service.loginOrRegister(oidc).getName());

        google("sub-2", "b@gmail.com", true, "X".repeat(100));
        assertEquals(60, service.loginOrRegister(oidc).getName().length());
    }
}