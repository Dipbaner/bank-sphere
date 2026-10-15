package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.AuthTokensResponse;
import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.exception.InvalidRefreshTokenException;
import com.nova.tech.bank.sphere.repository.RefreshTokenRepository;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.JwtService;
import com.nova.tech.bank.sphere.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repo;
    @Mock
    UserRepository userRepository;
    @Mock LockoutService lockoutService;
    @Mock
    JwtService jwtService;
    @Mock AuditService auditService;
    @InjectMocks RefreshTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshDays", 7L);
        ReflectionTestUtils.setField(service, "sessionMaxDays", 30L);

        user = new User();
        user.setId(1L);
        user.setEmail("a@b.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(Role.CUSTOMER);

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(jwtService.generateAccessToken(user)).thenReturn("access");
        lenient().when(jwtService.accessSeconds()).thenReturn(900L);
        lenient().when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });
    }

    private RefreshToken stored(String raw, boolean revoked, LocalDateTime expires, LocalDateTime started) {
        RefreshToken t = RefreshToken.builder()
                .id(5L).userId(1L).familyId("fam").tokenHash(TokenUtil.sha256(raw))
                .revoked(revoked).expiresAt(expires).sessionStartedAt(started)
                .createdAt(LocalDateTime.now()).build();
        when(repo.findByTokenHashForUpdate(TokenUtil.sha256(raw))).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void rotate_valid_retiresOldToken_andIssuesNewOne() {
        RefreshToken old = stored("raw", false, LocalDateTime.now().plusDays(1), LocalDateTime.now().minusDays(1));

        AuthTokensResponse res = service.rotate("raw");

        assertTrue(old.isRevoked());
        assertEquals(99L, old.getReplacedBy());
        assertEquals("access", res.accessToken());
        assertNotEquals("raw", res.refreshToken());
        assertEquals("CUSTOMER", res.role());
    }

    @Test
    void rotate_reusedToken_revokesWholeFamily_andThrows() {
        stored("raw", true, LocalDateTime.now().plusDays(1), LocalDateTime.now().minusDays(1));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));

        verify(repo).revokeFamily("fam");
        verify(auditService).log(eq(1L), any(), eq(AuditAction.TOKEN_REUSE_DETECTED), eq(AuditStatus.FAILURE), any());
    }

    @Test
    void rotate_unknownToken_throws() {
        when(repo.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("nope"));
    }

    @Test
    void rotate_expiredToken_throws_withoutRevokingFamily() {
        stored("raw", false, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusDays(8));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));
        verify(repo, never()).revokeFamily(any());
    }

    @Test
    void rotate_sessionOlderThanHardLimit_throws_andIssuesNothing() {
        stored("raw", false, LocalDateTime.now().plusDays(1), LocalDateTime.now().minusDays(31));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));
        verify(repo, never()).save(any());
    }

    @Test
    void rotate_disabledUser_revokesFamily_andThrows() {
        user.setStatus(UserStatus.DISABLED);
        stored("raw", false, LocalDateTime.now().plusDays(1), LocalDateTime.now().minusDays(1));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));
        verify(repo).revokeFamily("fam");
    }

    @Test
    void startSession_storesOnlyTheHash() {
        String raw = service.startSession(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertNotEquals(raw, captor.getValue().getTokenHash());
        assertEquals(TokenUtil.sha256(raw), captor.getValue().getTokenHash());
        assertFalse(captor.getValue().isRevoked());
    }
}
