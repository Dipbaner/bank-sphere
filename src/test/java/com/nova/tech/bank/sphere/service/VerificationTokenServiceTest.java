package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.TokenType;
import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.entity.VerificationToken;
import com.nova.tech.bank.sphere.exception.InvalidTokenException;
import com.nova.tech.bank.sphere.repository.VerificationTokenRepository;
import com.nova.tech.bank.sphere.util.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class VerificationTokenServiceTest {

    @Mock
    VerificationTokenRepository repository;
    @InjectMocks
    VerificationTokenService service;

    @Test
    void createToken_storesOnlyTheHash() {
        User user = new User();
        String raw = service.createToken(user, TokenType.EMAIL_VERIFY, Duration.ofHours(24));

        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(repository).save(captor.capture());

        assertNotEquals(raw, captor.getValue().getTokenHash());
        assertEquals(TokenUtil.sha256(raw), captor.getValue().getTokenHash());
    }

    @Test
    void consume_validToken_marksItUsed() {
        User user = new User();
        VerificationToken t = VerificationToken.builder()
                .user(user).type(TokenType.EMAIL_VERIFY).used(false)
                .tokenHash(TokenUtil.sha256("abc"))
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(repository.findByTokenHashAndType(TokenUtil.sha256("abc"), TokenType.EMAIL_VERIFY))
                .thenReturn(Optional.of(t));

        assertSame(user, service.consume("abc", TokenType.EMAIL_VERIFY));
        assertTrue(t.isUsed());
    }

    @Test
    void consume_expiredToken_isRejected() {
        VerificationToken t = VerificationToken.builder()
                .used(false).tokenHash(TokenUtil.sha256("abc")).type(TokenType.EMAIL_VERIFY)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(repository.findByTokenHashAndType(any(), any())).thenReturn(Optional.of(t));

        assertThrows(InvalidTokenException.class,
                () -> service.consume("abc", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_usedToken_isRejected() {
        VerificationToken t = VerificationToken.builder()
                .used(true).tokenHash(TokenUtil.sha256("abc")).type(TokenType.EMAIL_VERIFY)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(repository.findByTokenHashAndType(any(), any())).thenReturn(Optional.of(t));

        assertThrows(InvalidTokenException.class,
                () -> service.consume("abc", TokenType.EMAIL_VERIFY));
    }


    @Test
    void consume_unknownToken_isRejected() {
        when(repository.findByTokenHashAndType(any(), any())).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> service.consume("nope", TokenType.EMAIL_VERIFY));
    }
}
