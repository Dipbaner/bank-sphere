package com.nova.tech.bank.sphere.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

public class TotpServiceTest {

    // RFC test secret: the ASCII text "12345678901234567890" (20 bytes)
    private static final byte[] KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // same secret in Base32

    private static TotpService at(long epochSeconds) {
        return new TotpService(Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    @Test
    void hotp_matchesRfc4226Vectors() {
        assertEquals("755224", TotpService.hotp(KEY, 0, 6));
        assertEquals("287082", TotpService.hotp(KEY, 1, 6));
        assertEquals("359152", TotpService.hotp(KEY, 2, 6));
        assertEquals("520489", TotpService.hotp(KEY, 9, 6));
    }

    @Test
    void totp_matchesRfc6238Vectors_eightDigits() {
        assertEquals("94287082", TotpService.hotp(KEY, 59L / 30, 8));
        assertEquals("07081804", TotpService.hotp(KEY, 1111111109L / 30, 8));
        assertEquals("14050471", TotpService.hotp(KEY, 1111111111L / 30, 8));
        assertEquals("89005924", TotpService.hotp(KEY, 1234567890L / 30, 8));
        assertEquals("69279037", TotpService.hotp(KEY, 2000000000L / 30, 8));
        assertEquals("65353130", TotpService.hotp(KEY, 20000000000L / 30, 8));
    }

    @Test
    void currentCode_isSixDigitsAndMatchesKnownTime() {
        assertEquals("287082", at(59).currentCode(SECRET));            // last 6 digits of 94287082
        assertEquals("050471", at(1111111111L).currentCode(SECRET));   // last 6 digits of 14050471
    }

    /* ------------------------------------------------------- verification */

    @Test
    void verify_acceptsCurrentCode_andReturnsItsStep() {
        TotpService totp = at(1111111111L);
        OptionalLong step = totp.verify(SECRET, "050471", 0);
        assertTrue(step.isPresent());
        assertEquals(1111111111L / 30, step.getAsLong());
    }

    @Test
    void verify_acceptsPreviousAndNextStep_forClockDrift() {
        TotpService totp = at(1111111111L);
        long now = 1111111111L / 30;
        assertTrue(totp.verify(SECRET, TotpService.hotp(KEY, now - 1, 6), 0).isPresent());
        assertTrue(totp.verify(SECRET, TotpService.hotp(KEY, now + 1, 6), 0).isPresent());
    }

    @Test
    void verify_rejectsCodesTwoStepsAway_expiredOrTooEarly() {
        TotpService totp = at(1111111111L);
        long now = 1111111111L / 30;
        assertTrue(totp.verify(SECRET, TotpService.hotp(KEY, now - 2, 6), 0).isEmpty());
        assertTrue(totp.verify(SECRET, TotpService.hotp(KEY, now + 2, 6), 0).isEmpty());
    }

    @Test
    void verify_rejectsReplayOfAnAlreadyUsedCode() {
        TotpService totp = at(1111111111L);
        long used = totp.verify(SECRET, "050471", 0).getAsLong();
        // Same code again, now that 'used' is remembered:
        assertTrue(totp.verify(SECRET, "050471", used).isEmpty());
    }

    @Test
    void verify_rejectsOlderCodeAfterNewerWasUsed() {
        TotpService totp = at(1111111111L);
        long now = 1111111111L / 30;
        long used = totp.verify(SECRET, TotpService.hotp(KEY, now, 6), 0).getAsLong();
        assertTrue(totp.verify(SECRET, TotpService.hotp(KEY, now - 1, 6), used).isEmpty());
    }

    @Test
    void verify_rejectsWrongAndMalformedCodes() {
        TotpService totp = at(1111111111L);
        assertTrue(totp.verify(SECRET, "000000", 0).isEmpty());
        assertTrue(totp.verify(SECRET, "05047", 0).isEmpty());     // too short
        assertTrue(totp.verify(SECRET, "0504711", 0).isEmpty());   // too long
        assertTrue(totp.verify(SECRET, "05047a", 0).isEmpty());    // not digits
        assertTrue(totp.verify(SECRET, " 050471", 0).isEmpty());   // whitespace
        assertTrue(totp.verify(SECRET, "", 0).isEmpty());
        assertTrue(totp.verify(SECRET, null, 0).isEmpty());
    }

    @Test
    void verify_rejectsCodeFromADifferentSecret() {
        TotpService totp = at(1111111111L);
        String otherSecret = totp.generateSecret();
        assertTrue(totp.verify(otherSecret, "050471", 0).isEmpty());
    }

    @Test
    void generateSecret_is32Base32Chars_andUnique() {
        TotpService totp = at(0);
        String a = totp.generateSecret();
        String b = totp.generateSecret();
        assertEquals(32, a.length());
        assertTrue(a.matches("[A-Z2-7]{32}"));
        assertNotEquals(a, b);
    }

    @Test
    void buildOtpAuthUri_hasExpectedFormat() {
        String uri = at(0).buildOtpAuthUri("BankSphere", "dip@example.com", SECRET);
        assertEquals("otpauth://totp/BankSphere:dip%40example.com?secret=" + SECRET
                + "&issuer=BankSphere&algorithm=SHA1&digits=6&period=30", uri);
    }
}
