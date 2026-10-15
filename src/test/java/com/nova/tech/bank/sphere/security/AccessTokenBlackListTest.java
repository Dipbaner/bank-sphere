package com.nova.tech.bank.sphere.security;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class AccessTokenBlackListTest {

    @Test
    void revokedTokenIsReported_othersAreNot() {
        AccessTokenBlacklist list = new AccessTokenBlacklist();
        list.revoke("abc", Instant.now().plusSeconds(600));
        assertTrue(list.isRevoked("abc"));
        assertFalse(list.isRevoked("other"));
        assertFalse(list.isRevoked(null));
    }

    @Test
    void expiredEntriesAreRemovedOnNextRevoke() {
        AccessTokenBlacklist list = new AccessTokenBlacklist();
        list.revoke("old", Instant.now().minusSeconds(5));
        list.revoke("new", Instant.now().plusSeconds(600));
        assertFalse(list.isRevoked("old"));
        assertTrue(list.isRevoked("new"));
    }
}
