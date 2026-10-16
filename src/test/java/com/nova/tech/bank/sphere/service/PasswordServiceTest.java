package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.ChangePasswordRequest;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.InvalidOtpException;
import com.nova.tech.bank.sphere.exception.InvalidTokenException;
import com.nova.tech.bank.sphere.exception.PasswordException;
import com.nova.tech.bank.sphere.repository.PasswordHistoryRepository;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.AccessTokenBlacklist;
import com.nova.tech.bank.sphere.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasswordServiceTest {

    @Mock
    UserRepository userRepository;
    @Spy  PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(4);   // cheap cost for fast tests
    @Mock VerificationTokenService tokenService;
    @Mock MailService mailService;
    @Mock
    PasswordHistoryRepository historyRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock AuditService auditService;
    @Mock OtpService otpService;
    @Mock LockoutService lockoutService;
    @Mock
    AccessTokenBlacklist accessTokenBlacklist;
    @InjectMocks PasswordService service;

    private User user;

    private final AuthenticatedUser me =
            new AuthenticatedUser(1L, "dip@example.com", "jti-1", Instant.now());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resetMinutes", 15L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(service, "historyCount", 3);
        ReflectionTestUtils.setField(service, "lockMinutes", 15L);

        user = new User();
        user.setId(1L);
        user.setName("Dip");
        user.setEmail("dip@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setProvider(AuthProvider.LOCAL);
        user.setPasswordHash(passwordEncoder.encode("Old@Pass1"));
    }

    private ChangePasswordRequest change(String current, String next, String otp) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setCurrentPassword(current);
        r.setNewPassword(next);
        r.setOtp(otp);
        return r;
    }

    /* ---------------------------------------------------------- forgot */

    @Test
    void requestReset_unknownEmail_sendsNothing() {
        when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

        service.requestReset("X@Y.com");

        verify(mailService, never()).sendPasswordResetEmail(any(), any(), any());
        verify(tokenService, never()).createToken(any(), any(), any());
    }

    @Test
    void requestReset_activeUser_createsTokenAndSendsEmail() {
        when(userRepository.findByEmail("dip@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issuedRecently(any(), any(), any(), any())).thenReturn(false);
        when(tokenService.createToken(eq(user), eq(TokenType.PASSWORD_RESET), any())).thenReturn("rawtoken");

        service.requestReset("dip@example.com");

        verify(mailService).sendPasswordResetEmail("dip@example.com", "Dip", "rawtoken");
    }

    @Test
    void requestReset_withinCooldown_sendsNothing() {
        when(userRepository.findByEmail("dip@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issuedRecently(any(), any(), any(), any())).thenReturn(true);

        service.requestReset("dip@example.com");

        verify(mailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void requestReset_unverifiedAccount_sendsNothing() {
        user.setStatus(UserStatus.PENDING_EMAIL_VERIFICATION);
        when(userRepository.findByEmail("dip@example.com")).thenReturn(Optional.of(user));

        service.requestReset("dip@example.com");

        verify(mailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    /*----------------------------------------------------------- reset */

    @Test
    void resetPassword_valid_storesNewHash_archivesOld_revokesSessions() {
        String oldHash = user.getPasswordHash();
        when(tokenService.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);

        service.resetPassword("tok", "New@Pass9");

        assertTrue(passwordEncoder.matches("New@Pass9", user.getPasswordHash()));
        ArgumentCaptor<PasswordHistory> captor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(oldHash, captor.getValue().getPasswordHash());
        verify(refreshTokenService).logoutAll(1L);
        verify(mailService).sendPasswordChangedEmail("dip@example.com", "Dip");
    }

    @Test
    void resetPassword_sameAsCurrent_isRejected() {
        when(tokenService.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);

        PasswordException ex = assertThrows(PasswordException.class,
                () -> service.resetPassword("tok", "Old@Pass1"));

        assertEquals("PASSWORD_REUSED", ex.getCode());
        verify(historyRepository, never()).save(any());
        verify(refreshTokenService, never()).logoutAll(any());
    }

    @Test
    void resetPassword_matchingARecentPassword_isRejected() {
        PasswordHistory previous = PasswordHistory.builder()
                .userId(1L).passwordHash(passwordEncoder.encode("Prev@Pass1")).build();
        when(tokenService.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);
        when(historyRepository.findByUserIdOrderByIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(previous));

        PasswordException ex = assertThrows(PasswordException.class,
                () -> service.resetPassword("tok", "Prev@Pass1"));
        assertEquals("PASSWORD_REUSED", ex.getCode());
    }

    @Test
    void resetPassword_containingEmailName_isRejected() {
        user.setEmail("dipbanerjee@example.com");
        when(tokenService.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);

        PasswordException ex = assertThrows(PasswordException.class,
                () -> service.resetPassword("tok", "Dipbanerjee@1"));
        assertEquals("WEAK_PASSWORD", ex.getCode());
    }

    @Test
    void resetPassword_inactiveUser_isRejected() {
        user.setStatus(UserStatus.DISABLED);
        when(tokenService.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);

        assertThrows(InvalidTokenException.class, () -> service.resetPassword("tok", "New@Pass9"));
    }

    /* ---------------------------------------------------------- change */

    @Test
    void changePassword_wrongCurrentPassword_countsFailure_andSkipsOtp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(lockoutService.recordFailedLogin(1L)).thenReturn(false);

        PasswordException ex = assertThrows(PasswordException.class,
                () -> service.changePassword(me, change("Wrong@Pass1", "New@Pass9", "123456")));

        assertEquals("WRONG_CURRENT_PASSWORD", ex.getCode());
        verify(lockoutService).recordFailedLogin(1L);
        verify(otpService, never()).confirmCode(any(), any());
    }

    @Test
    void changePassword_valid_needsOtp_revokesEverySession() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.changePassword(me, change("Old@Pass1", "New@Pass9", "123456"));

        verify(otpService).confirmCode(1L, "123456");
        assertTrue(passwordEncoder.matches("New@Pass9", user.getPasswordHash()));
        verify(refreshTokenService).logoutAll(1L);
        verify(accessTokenBlacklist).revoke(eq("jti-1"), any());
        verify(mailService).sendPasswordChangedEmail("dip@example.com", "Dip");
    }

    @Test
    void changePassword_wrongOtp_leavesPasswordUnchanged() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new InvalidOtpException()).when(otpService).confirmCode(1L, "000000");

        assertThrows(InvalidOtpException.class,
                () -> service.changePassword(me, change("Old@Pass1", "New@Pass9", "000000")));

        assertTrue(passwordEncoder.matches("Old@Pass1", user.getPasswordHash()));
        verify(refreshTokenService, never()).logoutAll(any());
    }

    @Test
    void changePassword_reusedPassword_isRejectedBeforeTheOtpIsUsed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        PasswordException ex = assertThrows(PasswordException.class,
                () -> service.changePassword(me, change("Old@Pass1", "Old@Pass1", "123456")));

        assertEquals("PASSWORD_REUSED", ex.getCode());
        verify(otpService, never()).confirmCode(any(), any());
    }
}
